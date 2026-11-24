package com.centram.classzy.school.comm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps only a small set of formatting tags from message bodies (bold, italic, underline, lists, links). Everything else is dropped. */
final class Html {
    private Html() {}

    private static final Set<String> ALLOWED = Set.of("b", "strong", "i", "em", "u", "ul", "ol", "li", "p", "div", "br", "a");
    private static final Pattern TAG = Pattern.compile("<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_URL = Pattern.compile("^(https?://|mailto:)[^\\s\"'<>`\\\\]+$", Pattern.CASE_INSENSITIVE);

    static String clean(String in) {
        if (in == null) return "";
        String s = in.length() > 30000 ? in.substring(0, 30000) : in;
        StringBuilder out = new StringBuilder();
        Deque<String> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(escape(s.substring(last, m.start())));
            last = m.end();
            if (m.group(2) == null) continue; // comment
            String name = m.group(2).toLowerCase();
            boolean closing = !m.group(1).isEmpty();
            if (!ALLOWED.contains(name)) continue;
            if (name.equals("br")) { if (!closing) out.append("<br>"); continue; }
            if (closing) {
                if (open.contains(name)) { while (!open.isEmpty()) { String t = open.pop(); out.append("</").append(t).append('>'); if (t.equals(name)) break; } }
                continue;
            }
            if (name.equals("a")) {
                Matcher h = HREF.matcher(m.group(3));
                String url = null;
                if (h.find()) url = (h.group(1) != null ? h.group(1) : h.group(2)).trim();
                if (url == null || !SAFE_URL.matcher(url).matches()) continue;
                out.append("<a href=\"").append(url).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
            } else out.append('<').append(name).append('>');
            open.push(name);
        }
        out.append(escape(s.substring(last)));
        while (!open.isEmpty()) out.append("</").append(open.pop()).append('>');
        return out.toString().trim();
    }

    private static String escape(String t) { return t.replace("<", "&lt;").replace(">", "&gt;"); }

    /** Plain text of cleaned HTML, used to check a body isn't empty and for short previews. */
    static String text(String html) {
        if (html == null) return "";
        String t = html.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>", "\n").replaceAll("<[^>]*>", "");
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return t.replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").trim();
    }
}
