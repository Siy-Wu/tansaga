package com.siy.tenseiga.transform

import com.didiglobal.booster.kotlinx.touch
import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.ClassTransformer
import com.google.auto.service.AutoService
import com.siy.tenseiga.asmtools.forDebug
import com.siy.tenseiga.base.tools.asIterable
import com.siy.tenseiga.entity.TransformInfo
import com.siy.tenseiga.ext.getReport
import com.siy.tenseiga.ext.isCInitMethod
import com.siy.tenseiga.ext.isInitMethod
import com.siy.tenseiga.parser.TenseigaParser
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.PrintWriter


/**
 *
 * 具体的Class转换
 *
 * @author  Siy
 * @since  2022/5/26
 */
@AutoService(ClassTransformer::class)
class TenseigaClassTransform() : ClassTransformer {

    /**
     *日志输出流
     */
    private lateinit var logger: PrintWriter

    /**
     * hook转换的相关信息
     */
    private var transformInfo: TransformInfo? = null

    override fun onPreTransform(context: TransformContext) {
        this.logger = context.getReport("report.txt").touch().printWriter()
        this.logger.println("parse start")


        //获取class输入路径
        context.compileClasspath.asSequence().filter {
            it.isDirectory
        }.let {
            transformInfo = TenseigaParser().parse(it)

            logger.println(transformInfo.toString())
        }
    }

    /**
     * 注册转换相关的类
     */
    private fun registerTransform(): ClassNodeTransform? {

        var classNodeTransform: ClassNodeTransform? = null

        //注册一个ReplaceClassNodeTransform
        if (transformInfo?.replaceInfo?.isNotEmpty() == true) {
            classNodeTransform = ReplaceClassNodeTransform(
                transformInfo?.replaceInfo ?: listOf(),
                classNodeTransform
            )
        }

        //注册一个ProxyClassNodeTransform
        if (transformInfo?.proxyInfo?.isNotEmpty() == true) {
            classNodeTransform = ProxyClassNodeTransform(
                transformInfo?.proxyInfo ?: listOf(),
                classNodeTransform
            )
        }

        //注册一个SafeTryCatchHandlerNodeTransform
        if (transformInfo?.safeTryCatchHandlerInfo?.isNotEmpty() == true) {
            classNodeTransform = SafeTryCatchHandlerNodeTransform(
                transformInfo?.safeTryCatchHandlerInfo ?: listOf(),
                classNodeTransform
            )
        }

        //注册一个SerializableNodeTransform
        if (transformInfo?.serializableParserInfo?.isNotEmpty() == true) {
            classNodeTransform = SerializableNodeTransform(
                transformInfo?.serializableParserInfo ?: listOf(),
                classNodeTransform
            )
        }

        //注册一个InsertFunNodeTransform
        if (transformInfo?.insertFuncInfo?.isNotEmpty() == true){
            classNodeTransform = InsertFuncNodeTransform(
                transformInfo?.insertFuncInfo?: listOf(),
                classNodeTransform
            )
        }

        return classNodeTransform
    }


    override fun transform(context: TransformContext, klass: ClassNode): ClassNode {
        //如果没有注册任何转换器就直接返回

        val classNodeTransform = registerTransform() ?: return klass

        classNodeTransform.visitorClassNode(context, klass)
        klass.methods?.filter {
            !(isInitMethod(it) || isCInitMethod(it))
        }?.forEach {
            classNodeTransform.visitorMethod(context, klass,it)
            val  insnMethods =  it?.instructions?.iterator()?.asIterable()?.filterIsInstance(MethodInsnNode::class.java) ?: arrayListOf()
            insnMethods.forEach {insn->
                classNodeTransform.visitorInsnMethod(context, klass,it,insn)
            }
        }

        return klass
    }


    override fun onPostTransform(context: TransformContext) {
        super.onPostTransform(context)
        forDebug(context, transformInfo, logger)
        this.logger.close()
    }
}