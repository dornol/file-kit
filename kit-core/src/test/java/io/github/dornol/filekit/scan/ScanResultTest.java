package io.github.dornol.filekit.scan;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanResultTest {

    @Nested
    class FactoryMethods {

        @Test
        void clean_returnsCleanStatusWithNullMessage() {
            ScanResult result = ScanResult.clean();

            assertEquals(ScanResult.Status.CLEAN, result.status());
            assertNull(result.message());
        }

        @Test
        void infected_returnsInfectedStatusWithMessage() {
            ScanResult result = ScanResult.infected("EICAR-Test-File");

            assertEquals(ScanResult.Status.INFECTED, result.status());
            assertEquals("EICAR-Test-File", result.message());
        }

        @Test
        void error_returnsErrorStatusWithMessage() {
            ScanResult result = ScanResult.error("Connection timeout");

            assertEquals(ScanResult.Status.ERROR, result.status());
            assertEquals("Connection timeout", result.message());
        }
    }

    @Nested
    class RecordBehavior {

        @Test
        void equality_sameStatusAndMessage() {
            ScanResult a = new ScanResult(ScanResult.Status.CLEAN, null);
            ScanResult b = ScanResult.clean();

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void equality_infectedWithSameMessage() {
            ScanResult a = ScanResult.infected("virus-x");
            ScanResult b = ScanResult.infected("virus-x");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequality_differentStatus() {
            ScanResult clean = ScanResult.clean();
            ScanResult error = ScanResult.error("error");

            assertNotEquals(clean, error);
        }

        @Test
        void inequality_differentMessage() {
            ScanResult a = ScanResult.infected("virus-a");
            ScanResult b = ScanResult.infected("virus-b");

            assertNotEquals(a, b);
        }

        @Test
        void toString_containsStatusAndMessage() {
            ScanResult result = ScanResult.infected("Trojan.Gen");

            String str = result.toString();
            assertNotNull(str);
            // record toString includes field values
            assertEquals("ScanResult[status=INFECTED, message=Trojan.Gen]", str);
        }
    }

    @Nested
    class StatusEnum {

        @Test
        void allValuesPresent() {
            ScanResult.Status[] values = ScanResult.Status.values();
            assertEquals(3, values.length);
            assertEquals(ScanResult.Status.CLEAN, ScanResult.Status.valueOf("CLEAN"));
            assertEquals(ScanResult.Status.INFECTED, ScanResult.Status.valueOf("INFECTED"));
            assertEquals(ScanResult.Status.ERROR, ScanResult.Status.valueOf("ERROR"));
        }
    }

    @Nested
    class DirectConstruction {

        @Test
        void constructWithNullMessage() {
            ScanResult result = new ScanResult(ScanResult.Status.INFECTED, null);

            assertEquals(ScanResult.Status.INFECTED, result.status());
            assertNull(result.message());
        }

        @Test
        void constructWithEmptyMessage() {
            ScanResult result = new ScanResult(ScanResult.Status.ERROR, "");

            assertEquals(ScanResult.Status.ERROR, result.status());
            assertEquals("", result.message());
        }

        @Test
        void constructWithNullStatus_throws() {
            assertThrows(NullPointerException.class,
                    () -> new ScanResult(null, "message"));
        }
    }
}
