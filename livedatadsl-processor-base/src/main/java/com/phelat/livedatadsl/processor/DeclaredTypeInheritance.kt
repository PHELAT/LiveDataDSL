package com.phelat.livedatadsl.processor

import javax.lang.model.type.DeclaredType

data class DeclaredTypeInheritance(val son: DeclaredTypeInheritance?, val self: DeclaredType)
