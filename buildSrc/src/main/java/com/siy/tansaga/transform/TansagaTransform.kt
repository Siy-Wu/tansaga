package com.siy.tansaga.transform

import com.android.build.api.transform.TransformInvocation
import com.didiglobal.booster.kotlinx.asIterable
import com.didiglobal.booster.kotlinx.touch
import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.ClassTransformer
import com.siy.tansaga.BaseClassNodeTransform
import com.siy.tansaga.ProxyClassNodeTransform
import com.siy.tansaga.ReplaceClassNodeTransform
import com.siy.tansaga.TansagaParser
import com.siy.tansaga.entity.ProxyInfo
import com.siy.tansaga.entity.ReplaceInfo
import com.siy.tansaga.entity.TExtension
import com.siy.tansaga.entity.TransformInfo
import com.siy.tansaga.ext.*
import com.siy.tansaga.interfaces.ClassNodeTransform
import com.siy.tansaga.interfaces.TransformParser
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.stringtemplate.v4.compiler.Bytecode.instructions
import java.io.File
import java.io.PrintWriter
import java.lang.reflect.Method


/**
 *
 * @author  Siy
 * @since  2022/5/26
 */
class TansagaTransform(private val extension: TExtension) : ClassTransformer {

    private lateinit var logger: PrintWriter

    private var transformInfo: TransformInfo? = null

    override fun onPreTransform(context: TransformContext) {
        this.logger = getReport(context, "report.txt").touch().printWriter()

        (context as? TransformInvocation)?.inputs?.asSequence()?.map {
            it.jarInputs + it.directoryInputs
        }?.flatten()?.map { input ->
            input.file
        }?.filter {
            it.isDirectory
        }?.let {
            transformInfo = TansagaParser().parse(extension, it.iterator())

            logger.println(transformInfo.toString())
        }
    }

    override fun onPostTransform(context: TransformContext) {
        super.onPostTransform(context)
        this.logger.close()
    }

    private fun registerTransform(): ClassNodeTransform? {
        if (transformInfo?.isEmpty() != false) {
            return null
        }

        var classNodeTransform: ClassNodeTransform? = null

        if (transformInfo?.replaceInfo?.isNotEmpty() == true) {
            classNodeTransform = ReplaceClassNodeTransform(transformInfo?.replaceInfo ?: listOf(), classNodeTransform)
        }

        if (transformInfo?.proxyInfo?.isNotEmpty() == true) {
            classNodeTransform = ProxyClassNodeTransform(transformInfo?.proxyInfo ?: listOf(), classNodeTransform)
        }

        return classNodeTransform
    }


    override fun transform(context: TransformContext, klass: ClassNode): ClassNode {
        val classNodeTransform = registerTransform() ?: return klass

        klass.let {
            classNodeTransform.visitorClassNode(it)
            it
        }.methods?.filter {
            TypeUtil.isNormalMethod(it)
        }?.flatMap {
            classNodeTransform.visitorMethod(it)
            it?.instructions?.iterator()?.asIterable()?.filterIsInstance(MethodInsnNode::class.java) ?: arrayListOf()
        }?.forEach {
            classNodeTransform.visitorInsnMethod(it)
        }
        return klass
    }
}