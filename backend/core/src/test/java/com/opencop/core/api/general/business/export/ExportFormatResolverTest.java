// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ExportFormatResolverTest {

  @Test
  void selectsCsvMapperForCsvFormat() {
    var csvMapper = new CsvFeatureExportMapper();
    var resolver = new ExportFormatResolver(List.of(csvMapper));

    var selected = resolver.mapperFor(ExportRequest.Format.CSV);

    assertSame(csvMapper, selected);
  }

  @Test
  void resolvesContentTypeFromStoredFormat() {
    var resolver = new ExportFormatResolver(List.of(new CsvFeatureExportMapper()));

    assertEquals(new MediaType("text", "csv"), resolver.contentTypeFor("CSV"));
  }
}
