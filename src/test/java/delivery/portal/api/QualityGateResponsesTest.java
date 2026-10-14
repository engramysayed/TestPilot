package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class QualityGateResponsesTest {
    @Test
    public void structuredBodyHasErrorsAndQualityGate() {
        Map<String, Object> body = QualityGateResponses.fromErrors(List.of(
                "tcId 'TC_01': steps are blank after normalize"));
        assertEquals(body.get("error"), "QUALITY_GATE");
        assertEquals(body.get("code"), "QUALITY_GATE");
        assertTrue(body.get("errors") instanceof List);
        assertFalse(((List<?>) body.get("errors")).isEmpty());
        assertTrue(body.get("qualityGate") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> qg = (Map<String, Object>) body.get("qualityGate");
        assertEquals(qg.get("passed"), Boolean.FALSE);
        assertTrue(body.get("suggestions") instanceof List);
    }

    @Test
    public void fromExceptionUsesQualityGateDetail() {
        IllegalArgumentException e = GenerateQualityGate.failureException(
                List.of("No test cases generated"));
        Map<String, Object> body = QualityGateResponses.fromException(e);
        assertEquals(body.get("code"), "QUALITY_GATE");
        assertTrue(String.valueOf(body.get("message")).toLowerCase().contains("no test cases"));
    }
}
