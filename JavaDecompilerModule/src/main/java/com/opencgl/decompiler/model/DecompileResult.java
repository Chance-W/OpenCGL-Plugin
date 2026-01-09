package com.opencgl.decompiler.model;

/**
 * 反编译结果
 */
public class DecompileResult {
    private final boolean success;
    private final String sourceCode;
    private final String errorMessage;

    private DecompileResult(boolean success, String sourceCode, String errorMessage) {
        this.success = success;
        this.sourceCode = sourceCode;
        this.errorMessage = errorMessage;
    }

    public static DecompileResult success(String sourceCode) {
        return new DecompileResult(true, sourceCode, null);
    }

    public static DecompileResult failure(String errorMessage) {
        return new DecompileResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
