package com.alphawallet.app.entity

import com.alphawallet.app.entity.ContractType.*

/**
 * Describes an ABI function signature, including the parsed argument types and applicable contract categories.
 */
class FunctionData {
    val functionName: String
    val functionFullName: String
    var functionRawHex: String? = null
    val args: MutableList<String> = ArrayList()
    val hasSig: Boolean
    val contractType: MutableList<ContractType> = ArrayList()

    /**
    * Creates a function description when only the name is known.
    */
    constructor(fName: String, type: ContractType) {
        functionName = fName
        functionFullName = fName
        hasSig = false
        // parity with legacy implementation: type was not stored
    }

    /**
     * Parses a method signature (e.g. `transfer(address,uint256)`) into a function descriptor.
     */
    constructor(methodSig: String, type: ContractType, hasSignature: Boolean) {
        val b1Index = methodSig.indexOf("(")
        val b2Index = methodSig.lastIndexOf(")")
        functionName = if (b1Index >= 0) methodSig.substring(0, b1Index) else methodSig

        val argSegment = if (b1Index >= 0 && b2Index > b1Index) {
            methodSig.substring(b1Index + 1, b2Index)
        } else {
            ""
        }

        if (argSegment.isNotEmpty()) {
            val temp = argSegment.split(",")
            args.addAll(temp)
            temp.forEachIndexed { index, argument ->
                if (argument.contains("[]") || argument == "string" || argument == "bytes") {
                    args.add(argument)
                    args[index] = "nodata"
                }
            }
        }

        functionFullName = methodSig
        contractType.add(type)
        hasSig = hasSignature
    }

    /**
     * Associates an additional contract type with this function.
     */
    fun addType(type: ContractType) {
        contractType.add(type)
    }

    /**
     * Returns true when this function matches ERC-20 behaviour.
     */
    fun isERC20(): Boolean = contractType.contains(ERC20)

    /**
     * Returns true when this function matches either ERC-875 variant.
     */
    fun isERC875(): Boolean = contractType.contains(ERC875) || contractType.contains(ERC875_LEGACY)

    /**
     * Indicates whether this function is a contract constructor.
     */
    fun isConstructor(): Boolean = contractType.contains(CREATION)
}

