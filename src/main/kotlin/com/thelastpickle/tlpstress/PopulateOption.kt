package com.thelastpickle.tlpstress

sealed class PopulateOption {
    class Standard : PopulateOption()
    class Custom(val rows: Long, val deletes: Boolean = true) : PopulateOption()
}