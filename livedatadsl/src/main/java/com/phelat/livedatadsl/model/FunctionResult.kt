package com.phelat.livedatadsl.model

data class FunctionResult<out A, out B>(val observer: A, val liveData: B)
