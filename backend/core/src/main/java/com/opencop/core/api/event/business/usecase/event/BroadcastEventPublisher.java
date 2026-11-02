// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;
import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.MISSING_INFORMATION;

import com.opencop.core.api.event.business.policy.BroadcastEventValidation;
import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProvenanceHeaders;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.Contact;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public final class BroadcastEventPublisher {

  private final MessageBrokerProducer<Feature> producer;
  private final UserManager<InternalUser> userManager;
  private final PostGisService service;

  public BroadcastEventPublisher(
      MessageBrokerProducer<Feature> producer,
      UserManager<InternalUser> userManager,
      PostGisService service) {
    this.producer = producer;
    this.userManager = userManager;
    this.service = service;
  }

  OpenCopProperties requirePatchProperties(Feature event) {
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(event);
    if (properties == null
        || properties.getSource() == null
        || !StringUtils.hasText(properties.getObjectId())) {
      throw new OpencopEventException(MISSING_INFORMATION, HttpStatus.BAD_REQUEST);
    }
    BroadcastEventValidation.assertTimestampzValid(properties, false);
    return properties;
  }

  void assertObjectIdMatching(String objectId, OpenCopProperties properties) {
    if (!objectId.equals(properties.getObjectId())) {
      throw new GlobalOpenCopException(
          "Specified objectId does not match the event objectId specified", HttpStatus.BAD_REQUEST);
    }
  }

  Feature getLatestFeature(UUID containerId, String objectId) {
    try {
      return service.getLatestFeature(containerId, objectId);
    } catch (NotFoundException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  Feature publish(
      UUID containerId,
      String objectId,
      OpenCopProperties properties,
      GeoJsonObject geometry,
      FeatureSourceKind sourceKind) {
    var publishedFeature = OpenCopPropertiesParser.writeToFeature(properties, geometry);
    publish(containerId, objectId, publishedFeature, broadcastProvenance(sourceKind, properties));
    return publishedFeature;
  }

  void publish(
      UUID containerId, String objectId, Feature publishedFeature, FeatureProvenance provenance) {
    Map<String, String> headers = new HashMap<>();
    headers.put(OpencopKafkaHeaders.OBJECT_ID.getName(), objectId);
    headers.put(OpencopKafkaHeaders.SOURCE_ID.getName(), containerId.toString());
    headers.putAll(ProvenanceHeaders.toHeaders(provenance));
    this.producer.send(publishedFeature, headers);
  }

  /**
   * Provenance for a broadcast, which is an ingress in its own right rather than the output of a
   * mapper: only the source kind, the receipt time and a stable ingress identity are known.
   *
   * <p>The identity is the feature's own id, assigned before the send and carried in the payload,
   * so a redelivered record reports the same ingress instead of a second one. The receipt time is
   * likewise fixed here rather than at consumption, so a replay cannot report a later arrival than
   * the one that happened.
   */
  private static FeatureProvenance broadcastProvenance(
      FeatureSourceKind sourceKind, OpenCopProperties properties) {
    var eventId = properties.getId();
    return new FeatureProvenance(
        sourceKind,
        null,
        null,
        eventId == null ? null : eventId.toString(),
        null,
        Instant.now(),
        null);
  }

  void markStaleAndPublish(
      UUID containerId,
      String objectId,
      Feature latestFeature,
      OpenCopProperties properties,
      FeatureSourceKind sourceKind) {
    Instant now = Instant.now();
    if (properties.getTimestampz() == null) {
      properties.setTimestampz(new Timestampz());
      properties.getTimestampz().setStart(now.toEpochMilli());
      properties.getTimestampz().setTime(now.toEpochMilli());
    }
    properties.setId(UUID.randomUUID());
    properties.getTimestampz().setStale(now.toEpochMilli());

    var publishedFeature =
        OpenCopPropertiesParser.writeToFeature(properties, latestFeature.getGeometry());
    publish(containerId, objectId, publishedFeature, broadcastProvenance(sourceKind, properties));
  }

  OpenCopProperties hydrateProperties(UUID userId, String objectId, OpenCopProperties properties) {
    properties.setId(UUID.randomUUID());
    properties.setObjectId(objectId);

    var userInfo = this.userManager.get(userId);
    if (properties.getContacts() == null) {
      properties.setContacts(new ArrayList<>());
    }

    Instant now = Instant.now();
    properties.getTimestampz().setTime(now.toEpochMilli());

    var author =
        new Contact.Builder()
            .withUserId(userId)
            .withUsername(userInfo.username())
            .withEmail(userInfo.email())
            .withFirstName(userInfo.firstName())
            .withLastName(userInfo.lastName())
            .build();
    properties.setAuthor(author);

    boolean alreadyExists =
        properties.getContacts().stream().anyMatch(contact -> userId.equals(contact.getUserId()));

    if (!alreadyExists) {
      properties.getContacts().add(author);
    }

    properties.setKind("opencop.internal");
    properties.setVersion("1");

    return properties;
  }
}
