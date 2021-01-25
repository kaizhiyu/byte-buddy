package net.bytebuddy.description.method;

import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.FilterableList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementations represent a list of method descriptions. 实现表示方法描述的列表
 *
 * @param <T> The type of method descriptions represented by this list. 此列表表示的方法描述的类型
 */
public interface MethodList<T extends MethodDescription> extends FilterableList<T, MethodList<T>> {

    /**
     * Transforms the list of method descriptions into a list of detached tokens. All types that are matched by the provided
     * target type matcher are substituted by {@link net.bytebuddy.dynamic.TargetType}.
     *
     * @param matcher A matcher that indicates type substitution.
     * @return The transformed token list.
     */
    ByteCodeElement.Token.TokenList<MethodDescription.Token> asTokenList(ElementMatcher<? super TypeDescription> matcher);

    /**
     * Returns this list of these method descriptions resolved to their defined shape.
     *
     * @return A list of methods in their defined shape.
     */
    MethodList<MethodDescription.InDefinedShape> asDefined();

    /**
     * A base implementation of a {@link MethodList}.
     *
     * @param <S> The type of method descriptions represented by this list.
     */
    abstract class AbstractBase<S extends MethodDescription> extends FilterableList.AbstractBase<S, MethodList<S>> implements MethodList<S> {

        @Override
        protected MethodList<S> wrap(List<S> values) {
            return new Explicit<S>(values);
        }

        @Override
        public ByteCodeElement.Token.TokenList<MethodDescription.Token> asTokenList(ElementMatcher<? super TypeDescription> matcher) {
            List<MethodDescription.Token> tokens = new ArrayList<MethodDescription.Token>(size());
            for (MethodDescription methodDescription : this) {
                tokens.add(methodDescription.asToken(matcher));
            }
            return new ByteCodeElement.Token.TokenList<MethodDescription.Token>(tokens);
        }

        @Override
        public MethodList<MethodDescription.InDefinedShape> asDefined() {
            List<MethodDescription.InDefinedShape> declaredForms = new ArrayList<MethodDescription.InDefinedShape>(size());
            for (MethodDescription methodDescription : this) {
                declaredForms.add(methodDescription.asDefined());
            }
            return new Explicit<MethodDescription.InDefinedShape>(declaredForms);
        }
    }

    /**
     * A method list implementation that returns all loaded byte code methods (methods and constructors) that
     * are declared for a given type. 返回为给定类型声明的所有加载字节码方法（方法和构造函数）的方法列表实现
     */
    class ForLoadedMethods extends AbstractBase<MethodDescription.InDefinedShape> {

        /**
         * The loaded methods that are represented by this method list. 此方法列表表示的已加载方法
         */
        private final List<? extends Method> methods;

        /**
         * The loaded constructors that are represented by this method list. 此方法列表表示的已加载构造函数
         */
        private final List<? extends Constructor<?>> constructors;

        /**
         * Creates a new list for a loaded type. Method descriptions are created on demand. 为加载的类型创建新列表。方法描述按需创建
         *
         * @param type The type to be represented by this method list. 此方法列表要表示的类型
         */
        public ForLoadedMethods(Class<?> type) {
            this(type.getDeclaredConstructors(), type.getDeclaredMethods());
        }

        /**
         * Creates a method list that represents the given constructors and methods in their given order. The
         * constructors are assigned the indices before the methods. 创建一个方法列表，该列表按给定的顺序表示给定的构造函数和方法。构造函数在方法之前被分配了索引
         *
         * @param constructor The constructors to be represented by the method list.
         * @param method      The methods to be represented by the method list.
         */
        public ForLoadedMethods(Constructor<?>[] constructor, Method[] method) {
            this(Arrays.asList(constructor), Arrays.asList(method));
        }

        /**
         * Creates a method list that represents the given constructors and methods in their given order. The
         * constructors are assigned the indices before the methods. 创建一个方法列表，该列表按给定的顺序表示给定的构造函数和方法。构造函数在方法之前被分配了索引
         *
         * @param constructors The constructors to be represented by the method list.
         * @param methods      The methods to be represented by the method list.
         */
        public ForLoadedMethods(List<? extends Constructor<?>> constructors, List<? extends Method> methods) {
            this.constructors = constructors;
            this.methods = methods;
        }

        @Override
        public MethodDescription.InDefinedShape get(int index) {
            return index < constructors.size()
                    ? new MethodDescription.ForLoadedConstructor(constructors.get(index))
                    : new MethodDescription.ForLoadedMethod(methods.get(index - constructors.size()));

        }

        @Override
        public int size() {
            return constructors.size() + methods.size();
        }
    }

    /**
     * A method list that is a wrapper for a given list of method descriptions. 一种方法列表，它是给定方法描述列表的包装器
     *
     * @param <S> The type of method descriptions represented by this list. 此列表表示的方法描述的类型
     */
    class Explicit<S extends MethodDescription> extends AbstractBase<S> {

        /**
         * The list of methods that is represented by this method list.
         */
        private final List<? extends S> methodDescriptions;

        /**
         * Creates a new wrapper for a given list of methods.
         *
         * @param methodDescription The underlying list of methods used for this method list.
         */
        @SuppressWarnings("unchecked")
        public Explicit(S... methodDescription) {
            this(Arrays.asList(methodDescription));
        }

        /**
         * Creates a new wrapper for a given list of methods.
         *
         * @param methodDescriptions The underlying list of methods used for this method list.
         */
        public Explicit(List<? extends S> methodDescriptions) {
            this.methodDescriptions = methodDescriptions;
        }

        @Override
        public S get(int index) {
            return methodDescriptions.get(index);
        }

        @Override
        public int size() {
            return methodDescriptions.size();
        }
    }

    /**
     * A list of method descriptions for a list of detached tokens. For the returned method, each token is attached to its method representation. 分离标记列表的方法描述列表。对于返回的方法，每个标记都附加到其方法表示
     */
    class ForTokens extends AbstractBase<MethodDescription.InDefinedShape> {

        /**
         * The method's declaring type.
         */
        private final TypeDescription declaringType;

        /**
         * The list of method tokens to represent. 要表示的方法标记的列表
         */
        private final List<? extends MethodDescription.Token> tokens;

        /**
         * Creates a new list of method descriptions for a list of detached tokens. 为分离的标记列表创建新的方法描述列表
         *
         * @param declaringType The method's declaring type.
         * @param token         The list of method tokens to represent.
         */
        public ForTokens(TypeDescription declaringType, MethodDescription.Token... token) {
            this(declaringType, Arrays.asList(token));
        }

        /**
         * Creates a new list of method descriptions for a list of detached tokens. 为分离的标记列表创建新的方法描述列表
         *
         * @param declaringType The method's declaring type.
         * @param tokens        The list of method tokens to represent.
         */
        public ForTokens(TypeDescription declaringType, List<? extends MethodDescription.Token> tokens) {
            this.declaringType = declaringType;
            this.tokens = tokens;
        }

        @Override
        public MethodDescription.InDefinedShape get(int index) {
            return new MethodDescription.Latent(declaringType, tokens.get(index));
        }

        @Override
        public int size() {
            return tokens.size();
        }
    }

    /**
     * A list of method descriptions that yields {@link net.bytebuddy.description.method.MethodDescription.TypeSubstituting}.
     */
    class TypeSubstituting extends AbstractBase<MethodDescription.InGenericShape> {

        /**
         * The methods' declaring type.
         */
        protected final TypeDescription.Generic declaringType;

        /**
         * The list of method descriptions to represent.
         */
        protected final List<? extends MethodDescription> methodDescriptions;

        /**
         * The visitor to apply to each method description before returning it.
         */
        protected final TypeDescription.Generic.Visitor<? extends TypeDescription.Generic> visitor;

        /**
         * Creates a new type substituting method list.
         *
         * @param declaringType      The methods' declaring type.
         * @param methodDescriptions The list of method descriptions to represent.
         * @param visitor            The visitor to apply to each method description before returning it.
         */
        public TypeSubstituting(TypeDescription.Generic declaringType,
                                List<? extends MethodDescription> methodDescriptions,
                                TypeDescription.Generic.Visitor<? extends TypeDescription.Generic> visitor) {
            this.declaringType = declaringType;
            this.methodDescriptions = methodDescriptions;
            this.visitor = visitor;
        }

        @Override
        public MethodDescription.InGenericShape get(int index) {
            return new MethodDescription.TypeSubstituting(declaringType, methodDescriptions.get(index), visitor);
        }

        @Override
        public int size() {
            return methodDescriptions.size();
        }
    }

    /**
     * An implementation of an empty method list.
     *
     * @param <S> The type of parameter descriptions represented by this list.
     */
    class Empty<S extends MethodDescription> extends FilterableList.Empty<S, MethodList<S>> implements MethodList<S> {

        @Override
        public ByteCodeElement.Token.TokenList<MethodDescription.Token> asTokenList(ElementMatcher<? super TypeDescription> matcher) {
            return new ByteCodeElement.Token.TokenList<MethodDescription.Token>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public MethodList<MethodDescription.InDefinedShape> asDefined() {
            return (MethodList<MethodDescription.InDefinedShape>) this;
        }
    }
}
