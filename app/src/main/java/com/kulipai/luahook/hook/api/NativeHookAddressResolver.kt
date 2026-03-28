package com.kulipai.luahook.hook.api

internal object NativeHookAddressResolver {
    private const val ARM64_BRANCH_MASK = 0x7C00_0000
    private const val ARM64_BRANCH_OPCODE = 0x1400_0000

    private const val ARM64_NOP = 0xD503201F.toInt()
    private const val ARM64_BTI_C = 0xD503245F.toInt()
    private const val ARM64_PACIASP = 0xD503233F.toInt()
    private const val ARM64_PACIBSP = 0xD503237F.toInt()

    fun resolveArm64BranchWrapperTarget(entryAddress: Long, firstBytes: ByteArray): Long? {
        if (firstBytes.size < 4) {
            return null
        }

        decodeArm64Branch(entryAddress, readWordLittleEndian(firstBytes, 0))?.let { return it }

        if (firstBytes.size < 8) {
            return null
        }

        val firstWord = readWordLittleEndian(firstBytes, 0)
        if (!isArm64WrapperPrefix(firstWord)) {
            return null
        }

        return decodeArm64Branch(entryAddress + 4, readWordLittleEndian(firstBytes, 4))
    }

    private fun isArm64WrapperPrefix(word: Int): Boolean {
        return word == ARM64_NOP ||
                word == ARM64_BTI_C ||
                word == ARM64_PACIASP ||
                word == ARM64_PACIBSP
    }

    private fun decodeArm64Branch(instructionAddress: Long, instruction: Int): Long? {
        if ((instruction and ARM64_BRANCH_MASK) != ARM64_BRANCH_OPCODE) {
            return null
        }

        val imm26 = instruction and 0x03FF_FFFF
        val signedImm26 = if ((imm26 and 0x0200_0000) != 0) {
            imm26 or (-1 shl 26)
        } else {
            imm26
        }
        return instructionAddress + (signedImm26.toLong() shl 2)
    }

    private fun readWordLittleEndian(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
