package com.cleanpilot.service;

import com.cleanpilot.model.AppResidualInfo;
import com.cleanpilot.model.InstalledAppInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppUninstallerServiceTest {

    private final AppUninstallerService service = new AppUninstallerService();

    @Test
    void testScanInstalledAppsDoesNotThrow() {
        List<InstalledAppInfo> apps = service.scanInstalledApps();
        assertThat(apps).isNotNull();
    }

    @Test
    void testDeleteResidualCleanlyRemovesFolder(@TempDir Path tempDir) throws IOException {
        Path residualFolder = tempDir.resolve("OldUninstalledSoftware");
        Files.createDirectories(residualFolder.resolve("logs"));
        Files.writeString(residualFolder.resolve("logs").resolve("old.log"), "stale log data simulation");

        AppResidualInfo residual = new AppResidualInfo(
                "OldUninstalledSoftware",
                residualFolder,
                1024L,
                "AppData (Local)"
        );

        boolean deleted = service.deleteResidual(residual);
        assertThat(deleted).isTrue();
        assertThat(Files.exists(residualFolder)).isFalse();
    }

    @Test
    void testUnsafeResidualPathsAreBlocked() {
        // Roots and Windows paths must be rejected
        assertThat(service.isSafeResidualPath(Path.of("C:\\"))).isFalse();
        assertThat(service.isSafeResidualPath(Path.of("C:\\Windows"))).isFalse();
        assertThat(service.isSafeResidualPath(Path.of("C:\\Windows\\System32"))).isFalse();
        assertThat(service.isSafeResidualPath(Path.of("C:\\Program Files"))).isFalse();
        assertThat(service.isSafeResidualPath(Path.of("C:\\Users\\John"))).isFalse();

        // Valid AppData / ProgramData paths are accepted
        assertThat(service.isSafeResidualPath(Path.of("C:\\Users\\John\\AppData\\Local\\OldApp"))).isTrue();
        assertThat(service.isSafeResidualPath(Path.of("C:\\ProgramData\\Vendor\\ResidualApp"))).isTrue();

        // Execution against unsafe path returns false
        AppResidualInfo dangerousResidual = new AppResidualInfo(
                "SystemWindows", Path.of("C:\\Windows\\System32"), 1024L, "Windows"
        );
        assertThat(service.deleteResidual(dangerousResidual)).isFalse();
    }

    @Test
    void testParseCommandWithQuotedPathContainingSpaces() {
        String cmd = "\"C:\\Program Files\\Google\\Play Games\\Uninstaller.exe\" /o{47B07D71-505D-4665-AFD4-4972A30C6530}";
        AppUninstallerService.ParsedCommand parsed = AppUninstallerService.parseCommand(cmd);

        assertThat(parsed.executable()).isEqualTo("C:\\Program Files\\Google\\Play Games\\Uninstaller.exe");
        assertThat(parsed.arguments()).isEqualTo("/o{47B07D71-505D-4665-AFD4-4972A30C6530}");
    }

    @Test
    void testParseCommandWithSimpleQuotedPathWithoutArgs() {
        String cmd = "\"C:\\Program Files\\7-Zip\\Uninstall.exe\"";
        AppUninstallerService.ParsedCommand parsed = AppUninstallerService.parseCommand(cmd);

        assertThat(parsed.executable()).isEqualTo("C:\\Program Files\\7-Zip\\Uninstall.exe");
        assertThat(parsed.arguments()).isEmpty();
    }

    @Test
    void testParseCommandWithUnquotedPathContainingSpaces() {
        String cmd = "C:\\Program Files\\Vendor Name\\App\\Uninstall.exe /s /v";
        AppUninstallerService.ParsedCommand parsed = AppUninstallerService.parseCommand(cmd);

        assertThat(parsed.executable()).isEqualTo("C:\\Program Files\\Vendor Name\\App\\Uninstall.exe");
        assertThat(parsed.arguments()).isEqualTo("/s /v");
    }

    @Test
    void testParseCommandWithMsiExec() {
        String cmd = "MsiExec.exe /X{12345678-ABCD-EF01-2345-6789ABCDEF01}";
        AppUninstallerService.ParsedCommand parsed = AppUninstallerService.parseCommand(cmd);

        assertThat(parsed.executable()).isEqualTo("msiexec.exe");
        assertThat(parsed.arguments()).isEqualTo("/X{12345678-ABCD-EF01-2345-6789ABCDEF01}");
    }

    @Test
    void testParseCommandWithCmdWrapper() {
        String cmd = "CMD /C \"C:\\Program Files\\HP\\Documentation\\Doc_Uninstall.cmd\"";
        AppUninstallerService.ParsedCommand parsed = AppUninstallerService.parseCommand(cmd);

        assertThat(parsed.executable()).isEqualTo("cmd.exe");
        assertThat(parsed.arguments()).isEqualTo("/c C:\\Program Files\\HP\\Documentation\\Doc_Uninstall.cmd");
    }
}
