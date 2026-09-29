package dev.arkoder.inertia4j.springshared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds {@code Vary: Precognition} to every response, so caches keep Precognition validation responses apart
 * from regular ones. Register it for the routes handling Precognition requests.
 *
 * @see <a href="https://inertiajs.com/docs/v3/the-basics/forms#precognition">Inertia Precognition</a>
 */
public class PrecognitionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        response.addHeader("Vary", "Precognition");
        filterChain.doFilter(request, response);
    }
}
