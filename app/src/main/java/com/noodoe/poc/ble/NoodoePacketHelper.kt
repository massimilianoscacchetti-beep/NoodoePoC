package com.noodoe.poc.utils

import android.util.Log
import java.nio.charset.StandardCharsets

/**
 * Utility object per formattare e parsare pacchetti Noodoe BLE.
 * 
 * Struttura pacchetto (come documentato):
 * [0xA5] [MsgType] [TotalSegs] [SegIndex] [Payload UTF-8] [XOR Checksum]
 */
object NoodoePacketHelper {
    
    private const val TAG = "NoodoePacketHelper"
    
    // Costanti di framing
    const val HEADER_BYTE = 0xA5.toByte()
    const val MTU_DEFAULT = 20  // Default BLE MTU senza estensione
    
    // Message Type Identifiers
    enum class MessageType(val byteValue: Byte) {
        VOICE_CALL(0x01),
        SMS(0x02),
        WHATSAPP(0x03),
        APP_NOTIFICATION(0x04),
        COMMAND(0x10)  // Per comandi di controllo (come asset commit)
    }
    
    /**
     * Formatta un pacchetto singolo test Noodoe.
     * 
     * @param messageType Tipo di messaggio (es. APP_NOTIFICATION)
     * @param payload String UTF-8 da inviare
     * @param totalSegments Numero totale di segmenti (default 1 per pacchetti semplici)
     * @param segmentIndex Indice del segmento corrente (default 0 per pacchetti semplici)
     * @return ByteArray pronto per l'invio via BLE GATT
     */
    fun formatPacket(
        messageType: MessageType,
        payload: String,
        totalSegments: Byte = 0x01,
        segmentIndex: Byte = 0x00
    ): ByteArray {
        // Converti payload a UTF-8 bytes
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        
        // Limita il payload al MTU disponibile (20 bytes default - 5 bytes header)
        val maxPayloadSize = MTU_DEFAULT - 5
        val truncatedPayload = if (payloadBytes.size > maxPayloadSize) {
            payloadBytes.sliceArray(0 until maxPayloadSize)
        } else {
            payloadBytes
        }
        
        // Costruisci il pacchetto prima del checksum
        val packet = mutableListOf<Byte>()
        packet.add(HEADER_BYTE)                    // [0] Header 0xA5
        packet.add(messageType.byteValue)          // [1] Message Type
        packet.add(totalSegments)                  // [2] Total Segments
        packet.add(segmentIndex)                   // [3] Segment Index
        packet.addAll(truncatedPayload.toList())   // [4..N] Payload
        
        // Calcola XOR checksum su tutti i byte ECCETTO l'header
        val checksum = calculateXorChecksum(
            packet.drop(1).toByteArray()  // Escludi il header 0xA5
        )
        packet.add(checksum)                       // Ultimo byte: checksum
        
        return packet.toByteArray()
    }
    
    /**
     * Formatta un comando di commit asset per la caratteristica 0000155F.
     * Struttura: [0xA5, 0x10, Slot_ID, 0x00, Checksum]
     * 
     * @param slotId ID dello slot (0=Speedometer, 1=Clock Face, 2=Weather)
     * @return ByteArray del comando
     */
    fun formatAssetCommitCommand(slotId: Byte = 0x01): ByteArray {
        val packet = byteArrayOf(
            HEADER_BYTE,           // [0] Header 0xA5
            0x10,                  // [1] Command type (asset commit)
            slotId,                // [2] Slot ID
            0x00                   // [3] Reserved
        )
        
        // Checksum sui 4 byte del comando
        val checksum = calculateXorChecksum(packet.drop(1).toByteArray())
        
        return byteArrayOf(
            HEADER_BYTE,
            0x10,
            slotId,
            0x00,
            checksum
        )
    }
    
    /**
     * Calcola il checksum XOR per un payload Noodoe.
     * Algoritmo: XOR di tutti i byte nel payload.
     * 
     * @param payload ByteArray su cui calcolare lo XOR
     * @return Byte risultante dal XOR di tutti gli elementi
     */
    fun calculateXorChecksum(payload: ByteArray): Byte {
        var xor = 0x00.toByte()
        for (byte in payload) {
            xor = (xor.toInt() xor byte.toInt()).toByte()
        }
        return xor
    }
    
    /**
     * Converte un ByteArray in hex string per logging/debug.
     * 
     * @param bytes ByteArray da convertire
     * @return String hex (es: "A5 01 01 00 48 65...")
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { byte ->
            String.format("%02X", byte)
        }
    }
    
    /**
     * Log dettagliato di un pacchetto Noodoe per debug.
     */
    fun logPacket(bytes: ByteArray, label: String = "Packet") {
        Log.d(TAG, "=== $label ===")
        Log.d(TAG, "Raw Hex: ${bytesToHex(bytes)}")
        Log.d(TAG, "Length: ${bytes.size} bytes")
        
        if (bytes.size >= 5) {
            Log.d(TAG, "Header: 0x${String.format("%02X", bytes[0])}")
            Log.d(TAG, "MsgType: 0x${String.format("%02X", bytes[1])}")
            Log.d(TAG, "TotalSegs: ${bytes[2].toInt()}")
            Log.d(TAG, "SegIndex: ${bytes[3].toInt()}")
            
            val payloadSize = bytes.size - 5
            if (payloadSize > 0) {
                val payload = bytes.sliceArray(4 until bytes.size - 1)
                try {
                    Log.d(TAG, "Payload: ${String.fromCharCode(*payload.map { it.toInt() }.toIntArray())}")
                } catch (e: Exception) {
                    Log.d(TAG, "Payload (raw): ${bytesToHex(payload)}")
                }
            }
            
            Log.d(TAG, "Checksum: 0x${String.format("%02X", bytes.last())}")
        }
    }
}
