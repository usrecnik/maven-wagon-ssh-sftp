package com.abakus.maven.wagon.ssh_sftp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.function.Function;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

public class SftpUtility {
    private final String host;

    private final AuthenticationInfo authInfo;

    public SftpUtility(String host, AuthenticationInfo authInfo) {
        this.host = host;
        this.authInfo = authInfo;
    }

    public void put(File source, String destination) throws TransferFailedException {
        try {
            this.sftp(mkdirCmdList(destination) + "put \"" + source.getPath() + "\" \"" + destination + "\"", null);
        } catch (Exception e) {
            throw new TransferFailedException(e.getMessage());
        }
    }

    public void get(String resourceName, File destination) throws TransferFailedException {
        try {
            this.sftp("get \"" + resourceName + "\" \"" + destination.getPath() + "\"", null);
        } catch (Exception e) {
            throw new TransferFailedException(e.getMessage());
        }
    }

    public long lsTime(String resourceName) throws TransferFailedException, ResourceDoesNotExistException {
        final boolean[] notFound = new boolean[]{ false };
        final long[] timestamp = new long[]{ -1L };

        try {
            this.sftp("ls -l \"" + resourceName + "\"", (exitResult) -> {
                if (exitResult.stderr.contains("Can't ls:") && exitResult.stderr.contains("not found")) {
                    notFound[0] = true;
                    return true;
                } else {
                    exitResult.stdout.lines()
                            .filter((line) -> line.contains(resourceName) && !line.contains("sftp> ls -l "))
                            .forEach((line) -> {
                                line = line.replace(resourceName, "").trim();
                                final String dateStr = line.substring(line.length() - "Aug 31 11:49".length());
                                final String[] dateParts = dateStr.split(" ");
                                if (dateParts.length != 3) {
                                    throw new RuntimeException("Unable to parse date string from sftp [" + dateStr + "]");
                                } else {
                                    final String month = dateParts[0];
                                    final String day = dateParts[1];
                                    final String year;
                                    final String hour;
                                    final String minute;
                                    if (dateParts[2].contains(":")) {
                                        year = "" + Year.now().getValue();
                                        hour = dateParts[2].substring(0, dateParts[2].indexOf(":"));
                                        minute = dateParts[2].substring(dateParts[2].indexOf(":") + 1);
                                    } else {
                                        year = dateParts[2];
                                        hour = "01";
                                        minute = "01";
                                    }

                                    final String dateFmt = year + "-" + month + "-" + day + " " + hour + ":" + minute;
                                    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd HH:mm");

                                    try {
                                        timestamp[0] = sdf.parse(dateFmt).getTime();
                                    } catch (Throwable t) {
                                        throw new RuntimeException("Unable to parse date from string: [" + dateFmt + "].");
                                    }
                                }
                    });
                    return false;
                }
            });
        } catch (Exception e) {
            throw new TransferFailedException("Failed to get last changed time of [" + resourceName + "]: " + e.getMessage());
        }

        if (notFound[0])
            throw new ResourceDoesNotExistException("Resource [" + resourceName + "] does not exist on [" + this.host + "]");

        if (timestamp[0] < 0L)
            throw new TransferFailedException("Unable to obtain last change time for [" + resourceName + "] (ts=0)");

        return timestamp[0];
    }

    private void sftp(String command, Function<ExitResult, Boolean> func) throws Exception {
        this.exec(new String[]{
                "/usr/bin/sftp", "-i", this.authInfo.getPrivateKey(), "-b", "-",
                this.authInfo.getUserName() + "@" + this.host},
                command, func);
    }

    private void exec(String[] args, String stdin, Function<ExitResult, Boolean> func) throws ResourceDoesNotExistException, IOException, InterruptedException {
        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        final Process process = processBuilder.start();
        if (stdin != null) {
            try (OutputStream out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }

        final ExitResult result = new ExitResult();
        result.stdout = is2str(process.getInputStream());
        result.stderr = is2str(process.getErrorStream());
        result.exitCode = process.waitFor();
        boolean ignoreExitCode = false;
        if (func != null)
            ignoreExitCode = func.apply(result);

        if (!ignoreExitCode && result.exitCode != 0)
            throw new RuntimeException("sftp returned non-zero exit code (" + result.exitCode + "): " + result.stderr);
    }

    private static String is2str(InputStream stream) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static String mkdirCmdList(String destination) {
        final StringBuilder cmd = new StringBuilder();
        final StringBuilder sb = new StringBuilder();

        for (Path path : Paths.get(destination).getParent()) {
            sb.append("/").append(path.toString());
            cmd.append("-mkdir \"").append(sb).append("\"\n");
        }

        return cmd.toString();
    }

    private static class ExitResult {
        String stdout;
        String stderr;
        int exitCode;
    }
}
