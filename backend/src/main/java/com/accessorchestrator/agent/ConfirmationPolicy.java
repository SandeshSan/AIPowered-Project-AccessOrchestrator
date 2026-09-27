package com.accessorchestrator.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic check that the user's own words confirm an action. The LLM deciding "the user said yes" is
 * not enough on its own to create an access request; this is the second key.
 */
public final class ConfirmationPolicy {

    private static final Pattern NEGATIVE = Pattern.compile(
            "\\b(no|nope|nah|don'?t|do not|not now|cancel|stop|wait|hold on|never ?mind|decline)\\b");

    private static final Pattern AFFIRMATIVE = Pattern.compile(
            "\\b(yes|yeah|yep|yup|sure|ok|okay|confirm|confirmed|go ahead|please do|do it|submit( it| them)?"
                    + "|proceed|please submit|sounds good|correct|absolutely|of course)\\b");

    private ConfirmationPolicy() {
    }

    public static boolean isAffirmative(String message) {
        String m = normalize(message);
        return !NEGATIVE.matcher(m).find() && AFFIRMATIVE.matcher(m).find();
    }

    public static boolean isNegative(String message) {
        return NEGATIVE.matcher(normalize(message)).find();
    }

    private static String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).replace('’', '\'').trim();
    }
}
