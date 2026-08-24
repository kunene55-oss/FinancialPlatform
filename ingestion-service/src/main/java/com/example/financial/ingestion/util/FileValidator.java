package com.example.financial.ingestion.util;

import java.util.regex.Pattern;

public class FileValidator {
    private static final Pattern PATTERN = Pattern.compile("^transactions_\\d{8}\\.csv$");

    public static void validate(String filename) {
        if (filename == null || !PATTERN.matcher(filename).matches()) {
            throw new IllegalArgumentException("File name must match transactions_YYYYMMDD.csv");
        }
    }
}
