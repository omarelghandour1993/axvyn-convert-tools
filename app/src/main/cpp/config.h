#ifndef CONFIG_H
#define CONFIG_H

/* Minimal config for building libmp3lame on Android with NDK. */

#define STDC_HEADERS 1

#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_LIMITS_H 1
#define HAVE_MEMORY_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1

#define HAVE_MEMCPY 1
#define HAVE_STRCHR 1
#define HAVE_STRSTR 1

#define SIZEOF_SHORT 2
#define SIZEOF_UNSIGNED_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_UNSIGNED_INT 4
#if defined(__LP64__) || defined(_LP64) || defined(__aarch64__) || defined(__x86_64__)
#define SIZEOF_LONG 8
#define SIZEOF_UNSIGNED_LONG 8
#else
#define SIZEOF_LONG 4
#define SIZEOF_UNSIGNED_LONG 4
#endif
#define SIZEOF_LONG_LONG 8
#define SIZEOF_UNSIGNED_LONG_LONG 8
#define SIZEOF_FLOAT 4
#define SIZEOF_DOUBLE 8

/* Android/NDK has ieee754 floats. */
#define HAVE_IEEE754_FLOAT32_T 1
#ifndef IEEE754_FLOAT32_T
typedef float ieee754_float32_t;
#endif
#ifndef IEEE754_FLOAT64_T
typedef double ieee754_float64_t;
#endif

/* No mpglib decoder - we only use LAME for encoding. */
#undef HAVE_MPGLIB
#undef HAVE_NASM
#undef HAVE_XMMINTRIN_H

#define HAVE_GTK 0

/* Avoid termcap/frontend features */
#undef HAVE_TERMCAP
#undef HAVE_TERMCAP_H
#undef HAVE_NCURSES_TERMCAP_H

#endif /* CONFIG_H */
