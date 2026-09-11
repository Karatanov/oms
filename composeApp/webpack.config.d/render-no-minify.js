// Render's free builder has an 8 GB limit. Kotlin/Compose production code
// generation fits within it; terser minification does not.  Keep production
// runtime semantics and compact source maps off, but skip that final pass.
config.optimization.minimize = false;
