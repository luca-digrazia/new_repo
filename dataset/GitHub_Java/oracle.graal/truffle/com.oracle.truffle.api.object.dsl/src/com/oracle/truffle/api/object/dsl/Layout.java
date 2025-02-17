/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.object.dsl;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate an interface with {@link Layout} to generate an implementation of the interface which
 * uses object-model properties. {@link Layout} allows you to use the object-model in a similar way
 * to writing a normal Java class for statically declared and implementation-specific fields. Most
 * methods generated from an {@link Layout}-annotated interface are suitable for use on the
 * fast-path.
 *
 * <pre>
 * {@literal@}Layout
 * public interface RectLayout {
 *    ...
 * }
 * </pre>
 *
 * The generated class is named with the name of the interface and then {@code -Impl}. A singleton
 * instance of the interface, {@code -Impl.INSTANCE} is available as a static field in the class.
 *
 * <pre>
 * RectLayoutImpl.INSTANCE
 * </pre>
 *
 * <h2>Factory method</h2>
 *
 * A factory method named {@code create-} and then the name of the layout creates instances of the
 * layout. It returns a {@link DynamicObject}, not an instance of the interface.
 *
 * <pre>
 * DynamicObject createRect();
 * </pre>
 *
 * <h2>Guards</h2>
 *
 * Guards can tell you if an object is using layout. Guards are defined for {@link DynamicObject},
 * the more general {@link Object} which first checks if the arguments is a {@link DynamicObject},
 * and {@link ObjectType}, which you can get through the shape of a {@link DynamicObject}. To add a
 * guard, define the method in your interface.
 *
 * <pre>
 * boolean isRect(DynamicObject object);
 *
 * boolean isRect(Object object);
 *
 * boolean isRect(ObjectType objectType);
 * </pre>
 *
 * <h2>Properties</h2>
 *
 * To add properties, define a getter and setter, and add a parameter to the factory method.
 *
 * <pre>
 * DynamicObject createRect(int x, int y, int width, int height);
 *
 * ...
 *
 * int getX(DynamicObject object);
 *
 * void setX(DynamicObject object, int value);
 *
 * ...
 *
 * int getWidth(DynamicObject object);
 *
 * void setWidth(DynamicObject object, int value);
 *
 * ...
 * </pre>
 *
 * If you don't define a setter, the property will be final. This may improve the performance of the
 * property.
 *
 * <h2>Nullable Properties</h2>
 *
 * By default, properties are non-nullable, which means that they always need an instance of an
 * object and they cannot be assigned the value {@code null}. This has performance benefits in the
 * implementation of the object-model.
 *
 * To make a property nullable so that you can assign {@code null} to it, annotate the constructor
 * parameter with {@link Nullable}.
 *
 * <pre>
 * DynamicObject createObject(@Nullable Object nullableProperty);
 * </pre>
 *
 * <h2>Volatile Properties</h2>
 *
 * To define a property with volatile semantics, in the sense of the Java Language Specification §
 * 8.3.1.4, annotate the constructor parameter with {@link Volatile}. A property annotated as
 * volatile also allows you to define atomic operation methods in your layout interface for that
 * property. Methods available are {@code compareAndSet}, in the sense of
 * {@link AtomicReference#compareAndSet}, and {@code getAndSet}, in the sense of
 * {@link AtomicReference#getAndSet}.
 *
 * <pre>
 * boolean compareAndSetNAME(DynamicObject object, TYPE expected, TYPE newValue);
 *
 * TYPE getAndSet(DynamicObject object, TYPE value);
 * </pre>
 *
 * Volatile properties generally have lower performance than the default non-volatile properties.
 *
 * <h2>Semi-Final Properties</h2>
 *
 * It is possible to define a 'back-door' and unsafe setter for otherwise-final properties by
 * appending {@code -Unsafe} to the setter name.
 *
 * <pre>
 * void setValueUnsafe(DynamicObject object, Object value);
 * </pre>
 *
 * Final and semi-final properties may be assumed by a dynamic compiler to not change for a given
 * instance of an object after it is constructed. Unsafe setters are therefore unsafe as a
 * modification to the property could be ignored by the dynamic compiler. You should only use unsafe
 * setters if you have reasoned that it is not possible for the dynamic compiler to compile a
 * reference to the object and the property before the unsafe setter is used. One use-case is
 * closing cycles in class graphs, such as the classic class-of-class-is-class problem, where you
 * normally want the class property to be final for performance but just as the graph is created
 * this one cycle needs to be closed.
 *
 * Errors due to the incorrect use of unsafe getters are likely to be non-deterministic and
 * difficult to isolate. Consider making properties temporarily non-final with a conventional getter
 * if stale value are experienced in dynamically compiled code.
 *
 * <h2>Shape Properties</h2>
 *
 * A shape property is a property that is shared between many objects and does not frequently
 * change. One intended use-case is a property to store the class of an object, which is likely
 * shared between many objects and likely does not change after the object is created.
 *
 * Shape properties should be cached against an object's shape as there is an extra level of
 * indirection used to look up their value for an object. They may save space as they are not stored
 * for all instances.
 *
 * It is important to note that changing a shape-property for an existing object is both not a
 * fast-path operation, and depending on the design of your interpreter is likely ot invalidate
 * caches.
 *
 * When shape properties are used there is an extra level of indirection, in that a
 * {@link DynamicObjectFactory} (referred to as the shape, because it is the shape that the factory
 * object contains that is used to look up shape properties) is created by the layout and then used
 * when creating new instances. As shape properties are set and changed, multiple factories will be
 * created and it is up to the user to store and supply these as needed.
 *
 * Consider the example of a Java-style object, with a class and a hash code. The class would be a
 * shape property, as many objects will share the same class, and the hash code will be a normal
 * property.
 *
 * Shape properties are created by parameters in the method that creates the shape. The factory
 * method then accepts an instance of a factory when creating the object, which is how the instance
 * knows the value of the class property to use. A getter for a shape property can be defined as
 * normal.
 *
 * <pre>
 * &#64;Layout
 * interface JavaObjectLayout {
 *
 *     DynamicObjectFactory createJavaObjectShape(JavaClass klass);
 *
 *     DynamicObject createJavaObject(DynamicObjectFactory factory, int hashCode);
 *
 *     JavaClass getKlass(DynamicObject object);
 *
 *     int getHashCode(DynamicObject object);
 *
 * }
 * </pre>
 *
 * <pre>
 * JavaClass getKlass(DynamicObjectFactory factory);
 *
 * JavaClass getKlass(ObjectType objectType);
 * </pre>
 *
 * When we load our Java interpreter we need to set the class property of the {@code Class} object
 * to be itself. This means in this one isolated, slow-path, case we need to change a shape property
 * for an object that is already allocated. Getters for shape properties can be defined for the
 * {@link DynamicObjectFactory}, and for the {@link ObjectType}.
 *
 * Setters for shape properties are more complex, and they are not intended to be used in the fast
 * path. Setters can be defined on a {@link DynamicObjectFactory}, in which case they return a new
 * factory, or on a {@link DynamicObject}, in which they they change the shape of the object. This
 * is a slow-path operation and is likely to invalidate caches in your interpreter.
 *
 * <pre>
 * DynamicObjectFactory setKlass(DynamicObjectFactory factory, JavaClass value);
 *
 * void setKlass(DynamicObject object, JavaClass value);
 * </pre>
 *
 * Apply this to our example with Java classes:
 *
 * <pre>
 * javaClassObject = JavaObjectImpl.INSTANCE.createJavaObject(JavaObjectImpl.INSTANCE.createJavaObjectShape(null), defaultHashCode());
 * JavaObjectImpl.INSTANCE.setKlass(javaClassObject, javaClassObject);
 * </pre>
 *
 * <h2>Layout Inheritance</h2>
 *
 * Inheritance of layout interfaces allows you to model classical class inheritance, such as in a
 * language like Java. Use normal interface inheritance to make one layout inherit from another. You
 * then need to add the parameters for super-layouts at the beginning of sub-layout constructor
 * methods.
 *
 * Inherited shape properties work in a similar way.
 *
 * <pre>
 * &#64;Layout
 * interface Base {
 *
 *     DynamicObject createBaset(int a);
 *
 *     boolean isBase(DynamicObject object);
 *
 *     int getA(DynamicObject object);
 *
 *     void setA(DynamicObject object, int value);
 *
 * }
 *
 * &#64;Layout
 * interface Super implements Base {
 *
 *     DynamicObject createSuper(int a, int b);
 *
 *     int getB(DynamicObject object);
 *
 *     void setB(DynamicObject object, int value);
 * }
 *
 * DynamicObject object = SuperImpl.INSTANCE.createSuper(14, 2);
 * BaseImpl.INSTANCE.isBase(object); // returns true; base layout guards work on the super layout instance
 * BaseImpl.INSTANCE.getA(object); // returns 14; layout methods work on the super layout instance
 * </pre>
 *
 * <h2>Custom Object-Type Superclass</h2>
 *
 * Generated layouts use custom {@link ObjectType} subclasses internally. The default base class
 * that is inherited from is simply {@link ObjectType}. You can change this with the
 * {@link #objectTypeSuperclass} property on the {@link Layout} annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Layout {

    String objectTypeSuperclass() default "ObjectType";

}
