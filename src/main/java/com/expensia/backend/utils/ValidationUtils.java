package com.expensia.backend.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Generic validation utility class for parsing and validating request parameters
 * Reduces code duplication in controllers by providing reusable validation methods
 */
public class ValidationUtils {

    /**
     * Generic method to parse enum values with proper error handling
     * 
     * @param value String value to parse
     * @param enumClass Class of the enum to parse to
     * @param <T> Enum type
     * @return Parsed enum value or null if value is null/empty
     * @throws IllegalArgumentException if value is invalid
     */
    public static <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (isNullOrEmpty(value)) {
            return null;
        }
        
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("Invalid %s value: %s. Valid values are: %s", 
                    enumClass.getSimpleName().toLowerCase(), 
                    value, 
                    Arrays.toString(enumClass.getEnumConstants()))
            );
        }
    }

    /**
     * Generic method to parse values using a provided parser function
     * 
     * @param value String value to parse
     * @param parser Function that parses the string to the desired type
     * @param typeName Name of the type being parsed (for error messages)
     * @param formatExample Example of the expected format
     * @param <T> Return type
     * @return Parsed value or null if value is null/empty
     * @throws IllegalArgumentException if parsing fails
     */
    public static <T> T parseValue(String value, Function<String, T> parser, String typeName, String formatExample) {
        if (isNullOrEmpty(value)) {
            return null;
        }
        
        try {
            return parser.apply(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                String.format("Invalid %s format: %s. Expected format: %s", 
                    typeName, value, formatExample)
            );
        }
    }

    /**
     * Parse LocalDate from string with proper error handling
     * 
     * @param dateString String representation of date (YYYY-MM-DD)
     * @return LocalDate object or null if invalid/null
     * @throws IllegalArgumentException if date format is invalid
     */
    public static LocalDate parseDate(String dateString) {
        return parseValue(
            dateString, 
            LocalDate::parse, 
            "date", 
            "YYYY-MM-DD (e.g., 2024-01-15)"
        );
    }

    /**
     * Parse LocalDateTime from string with proper error handling
     * 
     * @param dateTimeString String representation of datetime (YYYY-MM-DDTHH:mm:ss)
     * @return LocalDateTime object or null if invalid/null
     * @throws IllegalArgumentException if datetime format is invalid
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        return parseValue(
            dateTimeString, 
            LocalDateTime::parse, 
            "datetime", 
            "YYYY-MM-DDTHH:mm:ss (e.g., 2024-01-15T14:30:00)"
        );
    }

    /**
     * Parse Double from string with proper error handling
     * 
     * @param numberString String representation of number
     * @return Double object or null if invalid/null
     * @throws IllegalArgumentException if number format is invalid
     */
    public static Double parseDouble(String numberString) {
        return parseValue(
            numberString, 
            Double::parseDouble, 
            "number", 
            "valid decimal number (e.g., 123.45)"
        );
    }

    /**
     * Parse Integer from string with proper error handling
     * 
     * @param numberString String representation of integer
     * @return Integer object or null if invalid/null
     * @throws IllegalArgumentException if number format is invalid
     */
    public static Integer parseInteger(String numberString) {
        return parseValue(
            numberString, 
            Integer::parseInt, 
            "integer", 
            "valid integer number (e.g., 123)"
        );
    }

    /**
     * Validate string is not null or empty/whitespace
     * 
     * @param value String to validate
     * @param fieldName Name of the field being validated
     * @return The trimmed string value
     * @throws IllegalArgumentException if value is null or empty
     */
    public static String validateRequired(String value, String fieldName) {
        if (isNullOrEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " is required and cannot be empty");
        }
        return value.trim();
    }

    /**
     * Validate string length is within specified bounds
     * 
     * @param value String to validate
     * @param fieldName Name of the field being validated
     * @param minLength Minimum allowed length
     * @param maxLength Maximum allowed length
     * @return The trimmed string value
     * @throws IllegalArgumentException if length is out of bounds
     */
    public static String validateLength(String value, String fieldName, int minLength, int maxLength) {
        if (value == null) {
            return null;
        }
        
        String trimmed = value.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                String.format("%s must be between %d and %d characters long", 
                    fieldName, minLength, maxLength)
            );
        }
        return trimmed;
    }

    /**
     * Validate number is within specified range
     * 
     * @param value Number to validate
     * @param fieldName Name of the field being validated
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @param <T> Number type that extends Comparable
     * @return The validated number
     * @throws IllegalArgumentException if value is out of range
     */
    public static <T extends Number & Comparable<T>> T validateRange(T value, String fieldName, T min, T max) {
        if (value == null) {
            return null;
        }
        
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                String.format("%s must be between %s and %s", fieldName, min, max)
            );
        }
        return value;
    }

    /**
     * Check if string is null, empty, or contains only whitespace
     * 
     * @param value String to check
     * @return true if null/empty/whitespace, false otherwise
     */
    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
