package net.bytebuddy.dynamic.scaffold;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.attribute.AnnotationValueFilter;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * A type initializer is responsible for defining a type's static initialization block.
 */
public interface TypeInitializer extends ByteCodeAppender {

    /**
     * Indicates if this type initializer is defined.
     *
     * @return {@code true} if this type initializer is defined.
     */
    boolean isDefined();

    /**
     * Expands this type initializer with another byte code appender. For this to be possible, this type initializer must
     * be defined.
     *
     * @param byteCodeAppender The byte code appender to apply as the type initializer.
     * @return A defined type initializer.
     */
    TypeInitializer expandWith(ByteCodeAppender byteCodeAppender);

    /**
     * Creates a method pool record that applies this type initializer while preserving the record that was supplied.
     *
     * @param record The record to wrap.
     * @return A new record that represents the supplied record while also executing this type initializer.
     */
    TypeWriter.MethodPool.Record wrap(TypeWriter.MethodPool.Record record);

    /**
     * A drain for writing a type initializer. 用于编写类型初始值设定项的排水管
     */
    interface Drain {

        /**
         * Applies the drain.
         *
         * @param classVisitor          The class visitor to apply the initializer to. 要应用初始值设定项的类访问者
         * @param typeInitializer       The type initializer to write. 要写入的类型初始值设定项
         * @param implementationContext The corresponding implementation context. 相应的实现上下文
         */
        void apply(ClassVisitor classVisitor, TypeInitializer typeInitializer, Implementation.Context implementationContext);

        /**
         * A default implementation of a type initializer drain that creates a initializer method. 创建初始值设定项方法的类型初始值设定项排出的默认实现
         */
        @HashCodeAndEqualsPlugin.Enhance
        class Default implements Drain {

            /**
             * The instrumented type.
             */
            protected final TypeDescription instrumentedType;

            /**
             * The method pool to use.
             */
            protected final TypeWriter.MethodPool methodPool;

            /**
             * The annotation value filter factory to use.
             */
            protected final AnnotationValueFilter.Factory annotationValueFilterFactory;

            /**
             * Creates a new default type initializer drain. 创建新的默认类型初始值设定项排出
             *
             * @param instrumentedType             The instrumented type.
             * @param methodPool                   The method pool to use.
             * @param annotationValueFilterFactory The annotation value filter factory to use.
             */
            public Default(TypeDescription instrumentedType,
                           TypeWriter.MethodPool methodPool,
                           AnnotationValueFilter.Factory annotationValueFilterFactory) {
                this.instrumentedType = instrumentedType;
                this.methodPool = methodPool;
                this.annotationValueFilterFactory = annotationValueFilterFactory;
            }

            @Override
            public void apply(ClassVisitor classVisitor, TypeInitializer typeInitializer, Implementation.Context implementationContext) {
                typeInitializer.wrap(methodPool.target(new MethodDescription.Latent.TypeInitializer(instrumentedType))).apply(classVisitor,
                        implementationContext,
                        annotationValueFilterFactory);
            }
        }
    }

    /**
     * Canonical implementation of a non-defined type initializer. 未定义类型初始值设定项的规范实现
     */
    enum None implements TypeInitializer {

        /**
         * The singleton instance.
         */
        INSTANCE;

        @Override
        public boolean isDefined() {
            return false;
        }

        @Override
        public TypeInitializer expandWith(ByteCodeAppender byteCodeAppenderFactory) {
            return new TypeInitializer.Simple(byteCodeAppenderFactory);
        }

        @Override
        public TypeWriter.MethodPool.Record wrap(TypeWriter.MethodPool.Record record) {
            return record;
        }

        @Override
        public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
            return new Size(0, 0);
        }
    }

    /**
     * A simple, defined type initializer that executes a given {@link ByteCodeAppender}.
     */
    @HashCodeAndEqualsPlugin.Enhance
    class Simple implements TypeInitializer {

        /**
         * The byte code appender to apply as the type initializer.
         */
        private final ByteCodeAppender byteCodeAppender;

        /**
         * Creates a new simple type initializer.
         *
         * @param byteCodeAppender The byte code appender to apply as the type initializer.
         */
        public Simple(ByteCodeAppender byteCodeAppender) {
            this.byteCodeAppender = byteCodeAppender;
        }

        @Override
        public boolean isDefined() {
            return true;
        }

        @Override
        public TypeInitializer expandWith(ByteCodeAppender byteCodeAppender) {
            return new TypeInitializer.Simple(new Compound(this.byteCodeAppender, byteCodeAppender));
        }

        @Override
        public TypeWriter.MethodPool.Record wrap(TypeWriter.MethodPool.Record record) {
            return record.prepend(byteCodeAppender);
        }

        @Override
        public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext, MethodDescription instrumentedMethod) {
            return byteCodeAppender.apply(methodVisitor, implementationContext, instrumentedMethod);
        }
    }
}
