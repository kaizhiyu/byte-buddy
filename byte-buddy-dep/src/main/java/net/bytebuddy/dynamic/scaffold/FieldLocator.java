package net.bytebuddy.dynamic.scaffold;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * A field locator offers an interface for locating a field that is declared by a specified type. 字段定位器提供用于定位由指定类型声明的字段的接口
 */
public interface FieldLocator {

    /**
     * Locates a field with the given name and throws an exception if no such type exists. 查找具有给定名称的字段，如果不存在此类类型，则引发异常
     *
     * @param name The name of the field to locate.
     * @return A resolution for a field lookup.
     */
    Resolution locate(String name);

    /**
     * Locates a field with the given name and type and throws an exception if no such type exists.
     *
     * @param name The name of the field to locate.
     * @param type The type fo the field to locate.
     * @return A resolution for a field lookup.
     */
    Resolution locate(String name, TypeDescription type);

    /**
     * A resolution for a field lookup.
     */
    interface Resolution {

        /**
         * Returns {@code true} if a field was located.
         *
         * @return {@code true} if a field was located.
         */
        boolean isResolved();

        /**
         * Returns the field description if a field was located. This method must only be called if
         * this resolution was actually resolved.
         *
         * @return The located field.
         */
        FieldDescription getField();

        /**
         * An illegal resolution.
         */
        enum Illegal implements Resolution {

            /**
             * The singleton instance.
             */
            INSTANCE;

            @Override
            public boolean isResolved() {
                return false;
            }

            @Override
            public FieldDescription getField() {
                throw new IllegalStateException("Could not locate field");
            }
        }

        /**
         * A simple implementation for a field resolution. 字段解析的简单实现
         */
        @HashCodeAndEqualsPlugin.Enhance
        class Simple implements Resolution {

            /**
             * A description of the located field. 定位字段的描述
             */
            private final FieldDescription fieldDescription;

            /**
             * Creates a new simple resolution for a field.
             *
             * @param fieldDescription A description of the located field.
             */
            protected Simple(FieldDescription fieldDescription) {
                this.fieldDescription = fieldDescription;
            }

            @Override
            public boolean isResolved() {
                return true;
            }

            @Override
            public FieldDescription getField() {
                return fieldDescription;
            }
        }
    }

    /**
     * A factory for creating a {@link FieldLocator}. 用于创建 {@link FieldLocator} 的工厂
     */
    interface Factory {

        /**
         * Creates a field locator for a given type. 为给定类型创建字段定位器
         *
         * @param typeDescription The type for which to create a field locator. 要为其创建字段定位器的类型
         * @return A suitable field locator. 合适的字段定位器
         */
        FieldLocator make(TypeDescription typeDescription);
    }

    /**
     * A field locator that never discovers a field. 永远不会发现一个字段的字段定位器
     */
    enum NoOp implements FieldLocator, Factory {

        /**
         * The singleton instance.
         */
        INSTANCE;

        @Override
        public FieldLocator make(TypeDescription typeDescription) {
            return this;
        }

        @Override
        public Resolution locate(String name) {
            return Resolution.Illegal.INSTANCE;
        }

        @Override
        public Resolution locate(String name, TypeDescription type) {
            return Resolution.Illegal.INSTANCE;
        }
    }

    /**
     * An abstract base implementation of a field locator. 字段定位器的抽象基实现
     */
    @HashCodeAndEqualsPlugin.Enhance
    abstract class AbstractBase implements FieldLocator {

        /**
         * The type accessing the field. 访问字段的类型
         */
        protected final TypeDescription accessingType;

        /**
         * Creates a new field locator.  创建新的字段定位器
         *
         * @param accessingType The type accessing the field.
         */
        protected AbstractBase(TypeDescription accessingType) {
            this.accessingType = accessingType;
        }

        @Override
        public Resolution locate(String name) {
            FieldList<?> candidates = locate(named(name).and(isVisibleTo(accessingType)));
            return candidates.size() == 1
                    ? new Resolution.Simple(candidates.getOnly())
                    : Resolution.Illegal.INSTANCE;
        }

        @Override
        public Resolution locate(String name, TypeDescription type) {
            FieldList<?> candidates = locate(named(name).and(fieldType(type)).and(isVisibleTo(accessingType)));
            return candidates.size() == 1
                    ? new Resolution.Simple(candidates.getOnly())
                    : Resolution.Illegal.INSTANCE;
        }

        /**
         * Locates fields that match the given matcher. 查找与给定匹配器匹配的字段
         *
         * @param matcher The matcher that identifies fields of interest. 识别关注字段的匹配器
         * @return A list of fields that match the specified matcher.
         */
        protected abstract FieldList<?> locate(ElementMatcher<? super FieldDescription> matcher);
    }

    /**
     * A field locator that only looks up fields that are declared by a specific type. 只查找由特定类型声明字段的字段定位器。
     */
    @HashCodeAndEqualsPlugin.Enhance
    class ForExactType extends AbstractBase {

        /**
         * The type for which to look up fields.
         */
        private final TypeDescription typeDescription;

        /**
         * Creates a new field locator for locating fields from a declared type.
         *
         * @param typeDescription The type for which to look up fields that is also providing the accessing type.
         */
        public ForExactType(TypeDescription typeDescription) {
            this(typeDescription, typeDescription);
        }

        /**
         * Creates a new field locator for locating fields from a declared type.
         *
         * @param typeDescription The type for which to look up fields.
         * @param accessingType   The accessing type.
         */
        public ForExactType(TypeDescription typeDescription, TypeDescription accessingType) {
            super(accessingType);
            this.typeDescription = typeDescription;
        }

        @Override
        protected FieldList<?> locate(ElementMatcher<? super FieldDescription> matcher) {
            return typeDescription.getDeclaredFields().filter(matcher);
        }

        /**
         * A factory for creating a {@link ForExactType}.
         */
        @HashCodeAndEqualsPlugin.Enhance
        public static class Factory implements FieldLocator.Factory {

            /**
             * The type for which to locate a field.
             */
            private final TypeDescription typeDescription;

            /**
             * Creates a new factory for a field locator that locates a field for an exact type.
             *
             * @param typeDescription The type for which to locate a field.
             */
            public Factory(TypeDescription typeDescription) {
                this.typeDescription = typeDescription;
            }

            @Override
            public FieldLocator make(TypeDescription typeDescription) {
                return new ForExactType(this.typeDescription, typeDescription);
            }
        }
    }

    /**
     * A field locator that looks up fields that are declared within a class's class hierarchy. 一种字段定位器，用于查找在类的类层次结构中声明的字段
     */
    @HashCodeAndEqualsPlugin.Enhance
    class ForClassHierarchy extends AbstractBase {

        /**
         * The type for which to look up a field within its class hierarchy. 要在其类层次结构中查找字段的类型
         */
        private final TypeDescription typeDescription;

        /**
         * Creates a field locator that looks up fields that are declared within a class's class hierarchy. 创建一个字段定位器，用于查找在类的类层次结构中声明的字段
         *
         * @param typeDescription The type for which to look up a field within its class hierarchy which is also the accessing type.
         */
        public ForClassHierarchy(TypeDescription typeDescription) {
            this(typeDescription, typeDescription);
        }

        /**
         * Creates a field locator that looks up fields that are declared within a class's class hierarchy. 在类层次结构中声明的字段中进行查找的定位器
         *
         * @param typeDescription The type for which to look up a field within its class hierarchy.
         * @param accessingType   The accessing type.
         */
        public ForClassHierarchy(TypeDescription typeDescription, TypeDescription accessingType) {
            super(accessingType);
            this.typeDescription = typeDescription;
        }

        @Override
        protected FieldList<?> locate(ElementMatcher<? super FieldDescription> matcher) {
            for (TypeDefinition typeDefinition : typeDescription) {
                FieldList<?> candidates = typeDefinition.getDeclaredFields().filter(matcher);
                if (!candidates.isEmpty()) {
                    return candidates;
                }
            }
            return new FieldList.Empty<FieldDescription>();
        }

        /**
         * A factory for creating a {@link ForClassHierarchy}. 用于创建 {@link ForClassHierarchy} 的工厂
         */
        public enum Factory implements FieldLocator.Factory {

            /**
             * The singleton instance.
             */
            INSTANCE;

            @Override
            public FieldLocator make(TypeDescription typeDescription) {
                return new ForClassHierarchy(typeDescription);
            }
        }
    }

    /**
     * A field locator that only locates fields in the top-level type. 仅定位顶级类型字段的字段定位器
     */
    class ForTopLevelType extends AbstractBase {

        /**
         * Creates a new type locator for a top-level type.
         *
         * @param typeDescription The type to access.
         */
        protected ForTopLevelType(TypeDescription typeDescription) {
            super(typeDescription);
        }

        @Override
        protected FieldList<?> locate(ElementMatcher<? super FieldDescription> matcher) {
            return accessingType.getDeclaredFields().filter(matcher);
        }

        /**
         * A factory for locating a field in a top-level type.
         */
        public enum Factory implements FieldLocator.Factory {

            /**
             * The singleton instance.
             */
            INSTANCE;

            @Override
            public FieldLocator make(TypeDescription typeDescription) {
                return new ForTopLevelType(typeDescription);
            }
        }
    }
}
