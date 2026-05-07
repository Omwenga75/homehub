package com.example.homehub.property

object PropertyDataHolder {
    private var propertyList: List<Property>? = null
    var selectedProperty: Property? = null
    fun setPropertyList(list: List<Property>) { propertyList = list }
    fun getPropertyList(): List<Property>? = propertyList
    fun clear() { 
        propertyList = null 
        selectedProperty = null
    }
}
