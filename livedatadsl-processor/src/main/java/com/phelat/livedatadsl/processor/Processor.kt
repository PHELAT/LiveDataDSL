package com.phelat.livedatadsl.processor

import com.phelat.livedatadsl.LiveData
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import java.io.IOException
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

class Processor : AbstractProcessor() {

    private lateinit var filer: Filer
    private lateinit var messager: Messager
    private lateinit var elementUtils: Elements

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
        elementUtils = processingEnv.elementUtils
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnvironment: RoundEnvironment
    ): Boolean {
        if (!roundEnvironment.processingOver()) {
            for (typeElement in getTypeElements(roundEnvironment.rootElements, annotations)) {
                processTypeElement(typeElement)
            }
        }
        return true
    }

    private fun processTypeElement(typeElement: TypeElement) {
        val packageName = elementUtils.getPackageOf(typeElement).qualifiedName.toString()
        val typeName = typeElement.simpleName.toString()
        val className = ClassName.get(packageName, typeName)
        val generatedClass = ClassName.get(packageName, "${typeName}_DSL")

        val classBuilder = TypeSpec.classBuilder(generatedClass).apply {
            addModifiers(Modifier.PUBLIC)
            superclass(className)
            generateBodyOfClass(typeElement, this)
        }

        try {
            JavaFile.builder(packageName, classBuilder.build())
                .build()
                .writeTo(filer)
        } catch (e: IOException) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.toString(), typeElement)
        }
    }

    private fun generateBodyOfClass(typeElement: TypeElement, classBuilder: TypeSpec.Builder) {
        val enclosedElements = typeElement.enclosedElements
        enclosedElements.forEachIndexed { index, element ->
            if (ElementKind.CONSTRUCTOR == element.kind) {
                generateConstructor(element, classBuilder)
            }
        }
    }

    private fun generateConstructor(element: Element, classBuilder: TypeSpec.Builder) {
        val constructor = element as ExecutableElement
        val constructorParameters = constructor.parameters

        val constructorBuilder = MethodSpec.constructorBuilder().apply {
            addModifiers(constructor.modifiers)
            val superCall = StringBuilder("super(")
            for (i in constructorParameters.indices) {
                val modifiers = constructorParameters[i].modifiers
                addParameter(
                    TypeName.get(constructorParameters[i].asType()),
                    constructorParameters[i].simpleName.toString(),
                    *modifiers.toTypedArray()
                )
                superCall.append(constructorParameters[i].simpleName.toString())
                if (i < constructorParameters.size - 1) {
                    superCall.append(", ")
                }
            }
            superCall.append(");")
            addCode(superCall.toString())
        }
        classBuilder.addMethod(constructorBuilder.build())
    }

    private fun getTypeElements(
        elements: Set<Element>,
        supportedAnnotations: Set<Element>
    ): Set<TypeElement> {
        val typeElements = HashSet<TypeElement>()
        for (element in elements) {
            if (element is TypeElement && processSubElements(element, supportedAnnotations)) {
                typeElements.add(element)
            }
        }
        return typeElements
    }

    private fun processSubElements(
        element: TypeElement,
        supportedAnnotations: Set<Element>
    ): Boolean {
        for (subElement in element.enclosedElements) {
            if (processElementAnnotations(subElement, supportedAnnotations)) {
                return true
            }
        }
        return false
    }

    private fun processElementAnnotations(
        subElement: Element,
        supportedAnnotations: Set<Element>
    ): Boolean {
        for (mirror in subElement.annotationMirrors) {
            if (processAnnotation(supportedAnnotations, mirror)) {
                return true
            }
        }
        return false
    }

    private fun processAnnotation(
        supportedAnnotations: Set<Element>,
        mirror: AnnotationMirror
    ): Boolean {
        for (annotation in supportedAnnotations) {
            if (mirror.annotationType.asElement() == annotation) {
                return true
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(LiveData::class.java.canonicalName)
    }

}
