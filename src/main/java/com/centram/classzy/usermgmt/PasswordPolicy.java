package com.centram.classzy.usermgmt;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.communication.CommSettingsStore;
import com.centram.classzy.communication.CommSpec;

import org.springframework.stereotype.Component;

/** Applies the institution's saved Password Policy (Security & Access) to passwords set from User Management. */
@Component
public class PasswordPolicy {

    private static final Set<String> COMMON = Set.of("password", "password1", "password123", "12345678", "123456789", "qwerty123", "qwertyuiop", "admin123", "welcome1", "iloveyou", "letmein123", "abc12345");
    private static final String UP = "ABCDEFGHJKLMNPQRSTUVWXYZ", LOW = "abcdefghijkmnpqrstuvwxyz", NUM = "23456789", SPECIAL = "!@#$%&*?";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CommSettingsStore store;

    public PasswordPolicy(CommSettingsStore store) { this.store = store; }

    private record Rules(int min, boolean upper, boolean lower, boolean number, boolean special, boolean blockCommon) {}

    private Rules rules(Long inst) {
        Map<String, String> saved = store.load(inst, "SEC_PASSWORD");
        var specs = CommSpec.section("SEC_PASSWORD");
        java.util.function.Function<String, String> get = k -> saved.getOrDefault(k, specs.get(k).def());
        return new Rules(Integer.parseInt(get.apply("minLength")), "true".equals(get.apply("requireUpper")), "true".equals(get.apply("requireLower")),
                "true".equals(get.apply("requireNumber")), "true".equals(get.apply("requireSpecial")), "true".equals(get.apply("blockCommon")));
    }

    /** Null when the password is acceptable, otherwise what is wrong with it. */
    public String violation(Long inst, String pw) {
        Rules r = rules(inst);
        if (pw == null || pw.length() < r.min) return "Use at least " + r.min + " characters.";
        if (pw.length() > 100) return "Use 100 characters or fewer.";
        if (r.upper && pw.chars().noneMatch(Character::isUpperCase)) return "Include an uppercase letter.";
        if (r.lower && pw.chars().noneMatch(Character::isLowerCase)) return "Include a lowercase letter.";
        if (r.number && pw.chars().noneMatch(Character::isDigit)) return "Include a number.";
        if (r.special && pw.chars().allMatch(Character::isLetterOrDigit)) return "Include a special character.";
        if (r.blockCommon && COMMON.contains(pw.toLowerCase())) return "This password is too common.";
        return null;
    }

    public String generate(Long inst) {
        Rules r = rules(inst);
        int len = Math.max(12, r.min);
        List<Character> chars = new ArrayList<>();
        chars.add(pick(UP)); chars.add(pick(LOW)); chars.add(pick(NUM)); chars.add(pick(SPECIAL));
        String all = UP + LOW + NUM + SPECIAL;
        while (chars.size() < len) chars.add(pick(all));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String set) { return set.charAt(RANDOM.nextInt(set.length())); }
}
