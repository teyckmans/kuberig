package io.kuberig.core.detection

import io.kuberig.core.model.GeneratorMethod
import io.kuberig.core.model.GeneratorMethodType
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class EnvResourceClassVisitor : ClassVisitor(Opcodes.ASM7) {

    var isAbstract = true
    var className = ""
    var superClassName = ""
    val generatorMethods = mutableListOf<GeneratorMethod>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        isAbstract = access and Opcodes.ACC_ABSTRACT != 0
        className = name
        if (superName != null) {
            superClassName = superName
        }
    }

    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
        if (innerClassIsNonStatic(name, access)) {
            isAbstract = true
        }
    }

    private fun innerClassIsNonStatic(name: String?, access: Int): Boolean {
        return name == this.className && access and Opcodes.ACC_STATIC == 0
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String?,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        return object : MethodVisitor(Opcodes.ASM7) {
                override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
                    if ("Lio/kuberig/annotations/EnvResource;" == desc) {
                        generatorMethods.add(GeneratorMethod(GeneratorMethodType.RESOURCE_RETURNING, name))
                    } else if ("Lio/kuberig/annotations/EnvResources;" == desc) {
                        generatorMethods.add(GeneratorMethod(GeneratorMethodType.RESOURCE_EMITTING, name))
                    }
                    return null
                }
            }
    }
}