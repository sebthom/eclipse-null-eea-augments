/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Sebastian Thomschke (Vegard IT GmbH) - initial API and implementation
 */
package org.lastnpe.eea.generator.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.lastnpe.eea.generator.internal.BytecodeAnalyzer.Nullability;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

/**
 * @author Sebastian Thomschke (Vegard IT GmbH)
 */
class BytecodeAnalyzerTest {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface ReturnValueNullability {
		Nullability value();
	}

	static final String STATIC_NONNULL_STRING = "HI";

	static @Nullable Object staticNullableObject;

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull1() {
		return "Hey";
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull10() {
		return new int[0];
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull11() {
		return new Object[0];
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static @Nullable String neverReturningNull12() {
		final Object str = "Hey";
		return (String) str;
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull2() {
		return new Object();
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull3() {
		return new String("Test");
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull4() {
		return new Object() + " test";
	}

	/**
	 * test method to ensure that `return null` in lambdas are not mistaken as null
	 * returns
	 */
	@ReturnValueNullability(Nullability.NEVER_NULL)
	static Object neverReturningNull6() {
		((Supplier<?>) () -> null).get();
		return "Hey";
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static void neverReturningNull7() {
		// nothing to do
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static boolean neverReturningNull8() {
		return true;
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	static @Nullable Object neverReturningNull9() {
		return STATIC_NONNULL_STRING;
	}

	@ReturnValueNullability(Nullability.UNKNOWN)
	static @Nullable Object returningMaybeNull1() {
		return new @Nullable Object[] { null }[0];
	}

	@ReturnValueNullability(Nullability.UNKNOWN)
	static @Nullable Object returningMaybeNull2() {
		final var env = System.getProperty("Abcdefg1234567");
		@SuppressWarnings("unused")
		final var unused = new Object();
		return env;
	}

	@ReturnValueNullability(Nullability.UNKNOWN)
	static @Nullable String returningMaybeNull3() {
		final @Nullable Object env = System.getProperty("Abcdefg1234567");
		return (String) env;
	}

	static @Nullable Object returningMaybeNull4() {
		return staticNullableObject;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable Object returningNull1() {
		return null;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable Object returningNull2() {
		if (System.currentTimeMillis() == 123)
			return "Hey";
		return null;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable Object returningNull3() {
		return System.currentTimeMillis() == 123 ? "Hey" : null;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable Object returningNull4(final boolean condition) {
		return condition ? "Hey" : null;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable Object returningNull5() {
		final String foo = null;
		return foo;
	}

	@ReturnValueNullability(Nullability.DEFINITLY_NULL)
	static @Nullable String returningNull6() {
		final Object str = null;
		return (String) str;
	}

	@ReturnValueNullability(Nullability.POLY_NULL)
	static @Nullable Object returningNullIfArgIsNull1(final @Nullable String arg1) {
		if (arg1 == null)
			return null;
		return "Hey";
	}

	@ReturnValueNullability(Nullability.POLY_NULL)
	static @Nullable Object returningNullIfArgIsNull2(final @Nullable String arg1, final @Nullable String arg2) {
		if (arg1 == null || arg2 == null)
			return null;
		return "Hey";
	}

	@ReturnValueNullability(Nullability.POLY_NULL)
	static @Nullable Object returningNullIfArgIsNull3(final @Nullable String arg1, final @Nullable String arg2) {
		if (arg1 == null && arg2 == null)
			return null;
		return "Hey";
	}

	@ReturnValueNullability(Nullability.POLY_NULL)
	static @Nullable Object returningNullIfArgIsNull4(final @Nullable String arg1) {
		if (arg1 == null)
			return arg1;
		return "Hey";
	}

	@ReturnValueNullability(Nullability.NEVER_NULL)
	public Object neverReturningNull5(final boolean condition) {
		if (condition)
			return new Object();
		return "Constant String";
	}

	@Test
	void testDetermineMethodReturnTypeNullability() {
		final var className = BytecodeAnalyzerTest.class.getName();
		try (ScanResult scanResult = new ClassGraph() //
				.enableAllInfo() //
				.enableSystemJarsAndModules() //
				.acceptClasses(className) //
				.scan()) {

			final var classInfo = scanResult.getClassInfo(className);
			assert classInfo != null;

			final var analyzer = new BytecodeAnalyzer(classInfo);

			Stream.of(getClass().getDeclaredMethods()).sorted(Comparator.comparing(Method::getName)).forEach(m -> {
				final var anno = m.getAnnotation(ReturnValueNullability.class);
				if (anno != null) {
					assertThat(
							analyzer.determineMethodReturnTypeNullability(classInfo.getMethodInfo(m.getName()).get(0)))
							.describedAs(m.getName()).isEqualTo(anno.value());
				}
			});
		}
	}
}
