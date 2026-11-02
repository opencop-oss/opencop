// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.layer.LayerDataImporter;
import com.opencop.core.api.general.technical.model.data.DataImportRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/layers")
public class LayerDataImportEndpoint {

  private final LayerDataImporter importer;

  LayerDataImportEndpoint(LayerDataImporter importer) {
    this.importer = importer;
  }

  @PostMapping(value = "/{layerId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> importDataMultipart(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      @PathVariable("layerId") final UUID layerId,
      @RequestPart("data") DataImportRequest data,
      @RequestPart("file") MultipartFile file)
      throws IOException {
    var request =
        new DataImportRequest(
            data.mapperId(), file.getBytes(), file.getOriginalFilename(), file.getContentType());
    importer.importData(Caller.of(principal), layerId, request);
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }
}
