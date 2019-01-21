package com.phelat.livedatadsl.processor

import com.phelat.livedatadsl.LiveData
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements

class Processor : AbstractProcessor() {

    private var filer: Filer? = null
    private var messager: Messager? = null
    private var elementUtils: Elements? = null

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
                // TODO : generate
            }
        }
        return true
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
