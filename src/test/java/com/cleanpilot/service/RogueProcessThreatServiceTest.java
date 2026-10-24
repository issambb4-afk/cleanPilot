package com.cleanpilot.service;

import com.cleanpilot.model.ProcessThreatInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RogueProcessThreatServiceTest {

    private NetworkOptimizationService mockNetworkService;
    private RogueProcessThreatService threatService;

    @BeforeEach
    void setUp() {
        mockNetworkService = Mockito.mock(NetworkOptimizationService.class);
        when(mockNetworkService.scanActiveSockets()).thenReturn(Collections.emptyList());
        threatService = new RogueProcessThreatService(mockNetworkService);
    }

    @Test
    @DisplayName("Should strictly protect core system processes from termination or suspension")
    void testProtectedProcessFailSafes() {
        // PID 0 and 4 are kernel/system
        assertThat(threatService.isProtectedProcess(0, "System Idle Process", "")).isTrue();
        assertThat(threatService.isProtectedProcess(4, "System", "")).isTrue();

        // Genuine Windows binaries in System32
        assertThat(threatService.isProtectedProcess(1200, "svchost.exe", "C:\\Windows\\System32\\svchost.exe")).isTrue();
        assertThat(threatService.isProtectedProcess(850, "csrss.exe", "C:\\Windows\\System32\\csrss.exe")).isTrue();
        assertThat(threatService.isProtectedProcess(450, "explorer.exe", "C:\\Windows\\explorer.exe")).isTrue();

        // Developer tooling exemption
        assertThat(threatService.isProtectedProcess(9999, "antigravity.exe", "C:\\Users\\User\\AppData\\Local\\antigravity.exe")).isTrue();

        // Fail-safe termination refusal
        boolean terminateBlocked = threatService.terminateRogueProcess(4);
        assertThat(terminateBlocked).isFalse();
    }

    @Test
    @DisplayName("Should detect masquerading system binaries executing in user land")
    void testMasqueradingDetection() {
        // svchost in AppData is fake / masquerading
        boolean fakeSvchost = threatService.isMasqueradingSystemBinary(
                "svchost.exe",
                "C:\\Users\\Anas\\AppData\\Local\\Temp\\svchost.exe"
        );
        assertThat(fakeSvchost).isTrue();

        // Legitimate svchost in System32 is NOT masquerading
        boolean realSvchost = threatService.isMasqueradingSystemBinary(
                "svchost.exe",
                "C:\\Windows\\System32\\svchost.exe"
        );
        assertThat(realSvchost).isFalse();

        // Fake csrss in ProgramData
        boolean fakeCsrss = threatService.isMasqueradingSystemBinary(
                "csrss.exe",
                "C:\\ProgramData\\csrss.exe"
        );
        assertThat(fakeCsrss).isTrue();

        // Fake explorer in Temp
        boolean fakeExplorer = threatService.isMasqueradingSystemBinary(
                "explorer.exe",
                "C:\\Users\\Public\\explorer.exe"
        );
        assertThat(fakeExplorer).isTrue();
    }

    @Test
    @DisplayName("Should detect deceptive double file extensions and typosquatting")
    void testDeceptiveExtensionsAndTyposquatting() {
        assertThat(threatService.hasDeceptiveExtension("C:\\Users\\User\\Downloads\\invoice.pdf.exe")).isTrue();
        assertThat(threatService.hasDeceptiveExtension("C:\\Users\\User\\Downloads\\contract.docx.exe")).isTrue();
        assertThat(threatService.hasDeceptiveExtension("C:\\Users\\User\\Downloads\\photo.jpg.exe")).isTrue();
        assertThat(threatService.hasDeceptiveExtension("C:\\Program Files\\App\\clean.exe")).isFalse();

        assertThat(threatService.hasTyposquattingName("scvhost.exe")).isTrue();
        assertThat(threatService.hasTyposquattingName("lssas.exe")).isTrue();
        assertThat(threatService.hasTyposquattingName("taskmgrr.exe")).isTrue();
        assertThat(threatService.hasTyposquattingName("notepad.exe")).isFalse();
    }

    @Test
    @DisplayName("Should calculate Shannon entropy accurately for encrypted/packed detection")
    void testShannonEntropyCalculation(@TempDir Path tempDir) throws IOException {
        // 1. Low entropy file (repeated zeroes)
        Path lowEntropyFile = tempDir.resolve("zeros.bin");
        byte[] zeros = new byte[10000];
        Files.write(lowEntropyFile, zeros);

        double lowEntropy = threatService.calculateFileEntropy(lowEntropyFile);
        assertThat(lowEntropy).isEqualTo(0.0);

        // 2. High entropy file (uniform pseudo-random bytes)
        Path highEntropyFile = tempDir.resolve("random.bin");
        byte[] randomBytes = new byte[65536];
        new Random(42).nextBytes(randomBytes);
        Files.write(highEntropyFile, randomBytes);

        double highEntropy = threatService.calculateFileEntropy(highEntropyFile);
        assertThat(highEntropy).isGreaterThan(7.5);
    }

    @Test
    @DisplayName("Should scan running processes without throwing exceptions")
    void testScanProcessesForThreatsDoesNotThrow() {
        List<ProcessThreatInfo> threats = threatService.scanProcessesForThreats();
        assertThat(threats).isNotNull();
        assertThat(threats).isNotEmpty();

        // System (PID 4) must always be present and marked protected
        ProcessThreatInfo systemProc = threats.stream()
                .filter(p -> p.pid() == 4)
                .findFirst()
                .orElse(null);

        if (systemProc != null) {
            assertThat(systemProc.isProtectedSystem()).isTrue();
            assertThat(systemProc.threatLevel()).isEqualTo(ProcessThreatInfo.ThreatLevel.SAFE);
            assertThat(systemProc.explanation()).isNotNull();
            assertThat(systemProc.explanation().anomalyRationale()).contains("expected");
        }
    }

    @Test
    @DisplayName("Should recognize developer runtimes and compilation tools")
    void testDeveloperToolRecognition() {
        assertThat(threatService.isRecognizedDeveloperTool("node.exe", "C:\\Program Files\\nodejs\\node.exe")).isTrue();
        assertThat(threatService.isRecognizedDeveloperTool("python.exe", "C:\\Users\\User\\AppData\\Local\\Programs\\Python\\python.exe")).isTrue();
        assertThat(threatService.isRecognizedDeveloperTool("rustc.exe", "C:\\Users\\User\\.cargo\\bin\\rustc.exe")).isTrue();
        assertThat(threatService.isRecognizedDeveloperTool("cargo.exe", "C:\\Users\\User\\.cargo\\bin\\cargo.exe")).isTrue();
        assertThat(threatService.isRecognizedDeveloperTool("java.exe", "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe")).isTrue();
        assertThat(threatService.isRecognizedDeveloperTool("evil_miner.exe", "C:\\Users\\User\\AppData\\Local\\Temp\\evil_miner.exe")).isFalse();
    }

    @Test
    @DisplayName("Should verify calibrated objective threat level labels")
    void testCalibratedThreatLevels() {
        assertThat(ProcessThreatInfo.ThreatLevel.CRITICAL.getDisplayName()).isEqualTo("Critical Anomaly");
        assertThat(ProcessThreatInfo.ThreatLevel.HIGH.getDisplayName()).isEqualTo("High-Confidence Anomaly");
        assertThat(ProcessThreatInfo.ThreatLevel.SUSPICIOUS.getDisplayName()).isEqualTo("Investigate (Suspicious)");
        assertThat(ProcessThreatInfo.ThreatLevel.SAFE.getDisplayName()).isEqualTo("Verified / Normal");
    }

    @Test
    @DisplayName("Should generate rich explainability analysis for flagged findings")
    void testThreatExplanationModel() {
        com.cleanpilot.model.ThreatExplanation explanation = new com.cleanpilot.model.ThreatExplanation(
                List.of("Executing from AppData directory", "Unsigned binary"),
                "Executable operates outside system directories without a verified code signature.",
                List.of("Portable software package", "In-house developer utility"),
                List.of("Inspect file properties", "Freeze process to safely inspect network sockets")
        );

        assertThat(explanation.observedIndicators()).hasSize(2);
        assertThat(explanation.anomalyRationale()).contains("outside system directories");
        assertThat(explanation.potentialLegitimateUses()).hasSize(2);
        assertThat(explanation.recommendedActions()).hasSize(2);
    }
}
