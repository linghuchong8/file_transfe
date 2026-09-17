package com.transfer.medical.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 子进程输出流消费线程，避免缓冲区写满导致外部进程挂起。
 */
@Slf4j
public class MedicalStreamGobbler extends Thread {

    private final InputStream stream;
    private final String name;

    public MedicalStreamGobbler(InputStream stream, String name) {
        this.stream = stream;
        this.name = name;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(name + ">" + line);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
