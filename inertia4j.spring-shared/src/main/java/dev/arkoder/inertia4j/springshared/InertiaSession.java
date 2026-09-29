package dev.arkoder.inertia4j.springshared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps data carried across a redirect, such as flash data and validation errors, in the HTTP session.
 * The data is consumed by the next response rendering a page.
 */
class InertiaSession {
    static final String FlashDataKey = "inertia.flash_data";
    static final String ErrorsKey = "inertia.errors";
    static final String PreserveFragmentKey = "inertia.preserve_fragment";
    static final String ClearHistoryKey = "inertia.clear_history";

    private InertiaSession() {}

    static void putAll(HttpServletRequest request, String key, Map<String, ?> values) {
        HttpSession session = request.getSession();
        Map<String, Object> merged = new LinkedHashMap<>(getMap(request, key));
        merged.putAll(values);
        session.setAttribute(key, merged);
    }

    static void setFlag(HttpServletRequest request, String key) {
        request.getSession().setAttribute(key, true);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getMap(HttpServletRequest request, String key) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Map.of();
        }

        Object value = session.getAttribute(key);

        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    static boolean getFlag(HttpServletRequest request, String key) {
        HttpSession session = request.getSession(false);

        return session != null && Boolean.TRUE.equals(session.getAttribute(key));
    }

    static void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        session.removeAttribute(FlashDataKey);
        session.removeAttribute(ErrorsKey);
        session.removeAttribute(PreserveFragmentKey);
        session.removeAttribute(ClearHistoryKey);
    }
}
