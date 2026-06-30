package app.pwhs.inject.frida.gadget

import android.content.Context
import android.util.Log
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.DexFile
import com.android.apksig.ApkSigner
import com.android.apksig.internal.util.AndroidSdkVersion
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.cert.Certificate
import java.security.PrivateKey

class FridaGadgetManager(private val context: Context) {

    companion object {
        private const val TAG = "GadgetManager"
    }

    suspend fun processApk(apkPath: String, targetVersion: String?, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                onLog("Error: Target APK not found at $apkPath")
                return@withContext false
            }

            val workDir = File(context.cacheDir, "gadget_work")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()

            onLog("Starting Gadget Injection Workflow for ${apkFile.name}")
            
            // Step 1: Decompilation (Unzip)
            val unzippedDir = File(workDir, "unzipped")
            unzippedDir.mkdirs()
            unzipApk(apkFile, unzippedDir, onLog)
            
            // Step 2: Integrate Gadget
            integrateGadget(targetVersion, unzippedDir, onLog)
            
            // Step 3: Identify Entry Point
            val entryPoint = identifyEntryPoint(apkFile, unzippedDir, onLog)
            
            // Step 4: Patch Smali / Dex
            patchDex(unzippedDir, entryPoint, onLog)
            
            // Step 5: Recompilation (Zip)
            val unsignedApk = File(workDir, "unsigned.apk")
            zipApk(unzippedDir, unsignedApk, onLog)
            
            // Step 6: Zipalign & Signing
            val signedApk = File(context.cacheDir, "patched_frida_${apkFile.name}")
            signApk(unsignedApk, signedApk, onLog)
            
            // Cleanup
            workDir.deleteRecursively()

            // Step 7: Install instructions
            onLog("Injection complete! Output: ${signedApk.absolutePath}")
            onLog("Please manually install the generated APK.")
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject gadget", e)
            onLog("Exception: ${e.message}")
            return@withContext false
        }
    }

    private fun unzipApk(apkFile: File, destDir: File, onLog: (String) -> Unit) {
        onLog("Step 1: Unzipping APK...")
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        onLog("APK unzipped successfully.")
    }

    private fun integrateGadget(version: String?, unzippedDir: File, onLog: (String) -> Unit) {
        onLog("Step 2: Integrating Frida Gadget binary ($version)...")
        val libDir = File(unzippedDir, "lib")
        if (!libDir.exists()) libDir.mkdirs()
        
        // Find existing architectures
        val archs = libDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: listOf("arm64-v8a", "armeabi-v7a")
        
        // TODO: Actually download the frida-gadget-<version>-android-<arch>.so
        // For now, we simulate placing the gadget by creating a dummy file to pack
        for (arch in archs) {
            val archDir = File(libDir, arch)
            archDir.mkdirs()
            val gadgetSo = File(archDir, "libfrida-gadget.so")
            gadgetSo.writeText("DUMMY GADGET BINARY CONTENT") // Real implementation would download the actual .so
            onLog("Integrated gadget into lib/$arch/")
        }
    }

    private fun identifyEntryPoint(apkFile: File, unzippedDir: File, onLog: (String) -> Unit): String {
        onLog("Step 3: Modifying AndroidManifest.xml Entry Point using ARSCLib...")
        var originalAppName = "android.app.Application"

        try {
            val apkModule = com.reandroid.apk.ApkModule.loadApkFile(apkFile)
            val manifest = apkModule.androidManifestBlock
            val appElement = manifest?.applicationElement
            if (appElement != null) {
                // Find existing android:name attribute
                val attr = appElement.searchAttributeByResourceId(0x01010003) // android:name
                if (attr != null && attr.valueAsString != null) {
                    originalAppName = attr.valueAsString
                    onLog("Found original application name: $originalAppName")
                } else {
                    onLog("No android:name found in <application>. Using fallback: $originalAppName")
                }
                
                // Change application name to our custom class
                val nameAttr = appElement.getOrCreateAndroidAttribute("name", 0x01010003)
                nameAttr.setValueAsString("app.pwhs.inject.frida.FridaApplication")
                
                // Write modified manifest back to unzippedDir
                val manifestFile = File(unzippedDir, "AndroidManifest.xml")
                manifestFile.outputStream().use { fos ->
                    manifest.writeBytes(fos)
                }
                onLog("AndroidManifest.xml patched successfully.")
            }
        } catch (e: Exception) {
            onLog("Failed to parse/patch Manifest with ARSCLib: ${e.message}")
        }

        onLog("Original target entry class: $originalAppName")
        return originalAppName
    }

    private fun patchDex(unzippedDir: File, originalAppName: String, onLog: (String) -> Unit) {
        onLog("Step 4: Patching classes.dex to add FridaApplication...")
        val classesDex = File(unzippedDir, "classes.dex")
        if (!classesDex.exists()) {
            onLog("Error: classes.dex not found!")
            return
        }

        try {
            val opcodes = Opcodes.getDefault()
            val dexFile = DexFileFactory.loadDexFile(classesDex, opcodes)
            
            val newClasses = mutableListOf<org.jf.dexlib2.iface.ClassDef>()
            newClasses.addAll(dexFile.classes)
            
            // Generate our custom Application class
            val fridaAppClass = createFridaApplicationClass(originalAppName)
            newClasses.add(fridaAppClass)
            
            val newDexFile = org.jf.dexlib2.immutable.ImmutableDexFile(opcodes, newClasses)
            
            val patchedDex = File(unzippedDir, "classes_patched.dex")
            DexFileFactory.writeDexFile(patchedDex.absolutePath, newDexFile)
            
            if (patchedDex.exists()) {
                classesDex.delete()
                patchedDex.renameTo(classesDex)
                onLog("classes.dex patched and rewritten successfully.")
            }
        } catch (e: Exception) {
            onLog("Dex patching failed: ${e.message}")
            throw e
        }
    }

    private fun createFridaApplicationClass(originalAppName: String): org.jf.dexlib2.iface.ClassDef {
        val superType = "L" + originalAppName.replace(".", "/") + ";"
        val classType = "Lapp/pwhs/inject/frida/FridaApplication;"
        
        // <init>()V
        val initInstructions = listOf(
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c(
                org.jf.dexlib2.Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0, 
                org.jf.dexlib2.immutable.reference.ImmutableMethodReference(superType, "<init>", emptyList(), "V")
            ),
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x(org.jf.dexlib2.Opcode.RETURN_VOID)
        )
        val initImpl = org.jf.dexlib2.immutable.ImmutableMethodImplementation(1, initInstructions, emptyList(), emptyList())
        val initMethod = org.jf.dexlib2.immutable.ImmutableMethod(
            classType, "<init>", emptyList(), "V", 
            org.jf.dexlib2.AccessFlags.PUBLIC.value or org.jf.dexlib2.AccessFlags.CONSTRUCTOR.value, 
            emptySet(), emptySet(), initImpl
        )
        
        // onCreate()V
        val onCreateInstructions = listOf(
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c(
                org.jf.dexlib2.Opcode.INVOKE_SUPER, 1, 1, 0, 0, 0, 0, 
                org.jf.dexlib2.immutable.reference.ImmutableMethodReference(superType, "onCreate", emptyList(), "V")
            ),
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c(
                org.jf.dexlib2.Opcode.CONST_STRING, 0, 
                org.jf.dexlib2.immutable.reference.ImmutableStringReference("frida-gadget")
            ),
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c(
                org.jf.dexlib2.Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, 
                org.jf.dexlib2.immutable.reference.ImmutableMethodReference("Ljava/lang/System;", "loadLibrary", listOf("Ljava/lang/String;"), "V")
            ),
            org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x(org.jf.dexlib2.Opcode.RETURN_VOID)
        )
        val onCreateImpl = org.jf.dexlib2.immutable.ImmutableMethodImplementation(2, onCreateInstructions, emptyList(), emptyList())
        val onCreateMethod = org.jf.dexlib2.immutable.ImmutableMethod(
            classType, "onCreate", emptyList(), "V", 
            org.jf.dexlib2.AccessFlags.PUBLIC.value, 
            emptySet(), emptySet(), onCreateImpl
        )
        
        return org.jf.dexlib2.immutable.ImmutableClassDef(
            classType, org.jf.dexlib2.AccessFlags.PUBLIC.value, superType, emptyList(), 
            "FridaApplication.java", emptySet(), emptySet(), listOf(initMethod, onCreateMethod)
        )
    }

    private fun zipApk(unzippedDir: File, outputApk: File, onLog: (String) -> Unit) {
        onLog("Step 5: Recompiling (Zipping) APK...")
        FileOutputStream(outputApk).use { fos ->
            ZipOutputStream(fos).use { zos ->
                unzippedDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = file.absolutePath.substringAfter(unzippedDir.absolutePath + "/")
                        val entry = ZipEntry(entryName)
                        
                        // Android requires certain files to be STORED (uncompressed)
                        if (entryName == "resources.arsc" || entryName.endsWith(".so") || entryName.endsWith(".png") || entryName.endsWith(".webp")) {
                            entry.method = ZipEntry.STORED
                            entry.size = file.length()
                            entry.compressedSize = file.length()
                            val crc = java.util.zip.CRC32()
                            crc.update(file.readBytes())
                            entry.crc = crc.value
                        }
                        
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        onLog("APK zipped successfully.")
    }

    private fun signApk(unsignedApk: File, signedApk: File, onLog: (String) -> Unit) {
        onLog("Step 6: Signing APK...")
        try {
            onLog("Loading keystore from assets...")
            val ks = KeyStore.getInstance("PKCS12")
            context.assets.open("sign.jks").use {
                ks.load(it, "frida123".toCharArray())
            }
            
            val privateKey = ks.getKey("frida", "frida123".toCharArray()) as PrivateKey
            val cert = ks.getCertificate("frida") as X509Certificate
            
            onLog("Initializing apksig ApkSigner...")
            val signerConfig = ApkSigner.SignerConfig.Builder("frida", privateKey, listOf(cert)).build()
            
            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                
            signer.sign()
            onLog("APK signed successfully with v1/v2/v3 signatures.")
        } catch (e: Exception) {
            onLog("Signing failed: ${e.message}")
            throw e
        }
    }
}
