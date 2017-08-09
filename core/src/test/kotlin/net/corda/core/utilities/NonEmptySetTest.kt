package net.corda.core.utilities

import com.google.common.collect.Lists
import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestStringSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import com.google.common.collect.testing.features.Feature
import junit.framework.TestSuite
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.initialiseTestSerialization
import net.corda.testing.resetTestSerialization
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        NonEmptySetTest.SingletonFactory::class,
        NonEmptySetTest.AtLeastTwoElementFactory::class,
        NonEmptySetTest.CopyOfFactory::class,
        NonEmptySetTest.General::class
)
class NonEmptySetTest {
    private companion object {
        fun createGuavaTestSuite(name: String, generator: (Array<out String>) -> NonEmptySet<String>, vararg features: Feature<*>): TestSuite {
            return SetTestSuiteBuilder
                    .using(object : TestStringSetGenerator() {
                        override fun create(elements: Array<out String>) = generator(elements)
                    })
                    .named(name)
                    .withFeatures(Lists.asList(CollectionFeature.ALLOWS_NULL_VALUES, CollectionFeature.KNOWN_ORDER, features))
                    .createTestSuite()
        }
    }

    object SingletonFactory {
        @JvmStatic
        fun suite(): TestSuite = createGuavaTestSuite("of[element]", { NonEmptySet.of(it.single()) }, CollectionSize.ONE)
    }

    object AtLeastTwoElementFactory {
        @JvmStatic
        fun suite(): TestSuite {
            return createGuavaTestSuite(
                    "of[first, second, rest]",
                    { NonEmptySet.of(it[0], it[1], *it.copyOfRange(2, it.size)) },
                    CollectionSize.SEVERAL)
        }
    }

    object CopyOfFactory {
        @JvmStatic
        fun suite(): TestSuite {
            return createGuavaTestSuite("copyOf", { NonEmptySet.copyOf(it.asList()) }, CollectionSize.ONE, CollectionSize.SEVERAL)
        }
    }

    class General {
        @Test
        fun `copyOf - empty source`() {
            assertThatThrownBy { NonEmptySet.copyOf(HashSet<Int>()) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun head() {
            assertThat(NonEmptySet.of(1).head()).isEqualTo(1)
            assertThat(NonEmptySet.of(1, 2).head()).isEqualTo(1)
            assertThat(NonEmptySet.of(1, 2, 3).head()).isEqualTo(1)
            assertThat(NonEmptySet.copyOf(listOf(1)).head()).isEqualTo(1)
            assertThat(NonEmptySet.copyOf(listOf(1, 2)).head()).isEqualTo(1)
        }

        @Test
        fun `serialize deserialize`() {
            initialiseTestSerialization()
            try {
                val original = NonEmptySet.of(-17, 22, 17)
                val copy = original.serialize().deserialize()
                assertThat(copy).isEqualTo(original).isNotSameAs(original)
            } finally {
                resetTestSerialization()
            }
        }
    }
}
