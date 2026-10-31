package com.tickethub.auth.dto.request;

/** One place for the password policy (8-64 chars, upper + lower + digit + special). */
public final class PasswordRules {
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String MESSAGE =
            "must be 8-64 characters and contain an upper-case letter, a lower-case letter, a digit and a special character";

    private PasswordRules() {}
}
