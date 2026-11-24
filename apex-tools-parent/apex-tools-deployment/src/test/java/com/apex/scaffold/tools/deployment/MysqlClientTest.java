package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MysqlClientTest {

    private static final int PIPE_PAYLOAD_SIZE = 262_144;

    @Test
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void drainsBothOutputPipesBeforeChildReadsInput() throws Exception {
        MysqlClient client = new MysqlClient(
                List.of(
                        "/bin/sh",
                        "-c",
                        """
                        awk 'BEGIN { for (i = 0; i < 262144; i++) printf "E" }' >&2
                        awk 'BEGIN { for (i = 0; i < 262144; i++) printf "O" }'
                        wc -c | tr -d ' '
                        """),
                "test");

        assertEquals("O".repeat(PIPE_PAYLOAD_SIZE) + PIPE_PAYLOAD_SIZE, client.execute("Q".repeat(PIPE_PAYLOAD_SIZE)));
    }

    @Test
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void withholdsChildOutputOnFailure() {
        MysqlClient client = new MysqlClient(
                List.of("/bin/sh", "-c", "printf private-stdout; printf private-stderr >&2; exit 7"), "test");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.execute("SELECT 1"));
        assertTrue(failure.getMessage().contains("output withheld"));
        assertFalse(failure.getMessage().contains("private-"));
    }

    @Test
    @Timeout(value = 8, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void timesOutWhenWrapperChildKeepsInputPipeOpenWithoutReading() {
        MysqlClient client = new MysqlClient(
                List.of("/bin/sh", "-c", "exec 3<&0; sleep 12 <&3 & wait"), "test", Duration.ofSeconds(2));

        TimeoutException failure =
                assertThrows(TimeoutException.class, () -> client.execute("Q".repeat(PIPE_PAYLOAD_SIZE)));
        assertEquals("mysql command timed out after 2 seconds", failure.getMessage());
    }
}
