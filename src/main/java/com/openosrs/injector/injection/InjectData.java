/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this submodule.
 */
package com.openosrs.injector.injection;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.injectors.Injector;
import com.openosrs.injector.rsapi.RSApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.Getter;
import net.runelite.asm.ClassFile;
import net.runelite.asm.ClassGroup;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.signature.Signature;
import net.runelite.asm.signature.util.VirtualMethods;
import net.runelite.deob.DeobAnnotations;
import net.runelite.deob.deobfuscators.Renamer;
import net.runelite.deob.util.NameMappings;

/**
 * Abstract class meant as the interface of {@link com.openosrs.injector.Injection injection} for injectors
 */
public abstract class InjectData
{
	public static final String CALLBACKS = "net/runelite/api/hooks/Callbacks";

	@Getter
	private final ClassGroup vanilla;

	@Getter
	private final ClassGroup deobfuscated;

	@Getter
	private final ClassGroup mixins;

	@Getter
	private final RSApi rsApi;

	public InjectData(ClassGroup vanilla, ClassGroup deobfuscated, ClassGroup mixins, RSApi rsApi)
	{
		this.vanilla = vanilla;
		this.deobfuscated = deobfuscated;
		this.rsApi = rsApi;
		this.mixins = mixins;

		removeRuneliteClasses();
		remap();
		initToVanilla();
	}

	/**
	 * Deobfuscated ClassFiles -> Vanilla ClassFiles
	 */
	@Getter
	private final Map<ClassFile, ClassFile> toVanilla = new HashMap<>();

	/**
	 * Strings -> Deobfuscated ClassFiles
	 * keys:
	 * - Obfuscated name
	 * - RSApi implementing name
	 */
	public final Map<String, ClassFile> toDeob = new HashMap<>();

	public abstract void runChildInjector(Injector injector);

	private void removeRuneliteClasses() {
		final var toRemove = new ArrayList<ClassFile>();
		for (ClassFile classFile : this.vanilla) {
			if (classFile.getName().contains("runelite")) {
				toRemove.add(classFile);
			}
		}
		toRemove.forEach(this.vanilla::removeClass);
	}

	private void remap() {
		final var mappings = new NameMappings();
		var nameIndex = 0;
		for (ClassFile classFile : this.vanilla) {
			for (Method method : classFile.getMethods()) {
				final var nameAnn = method.getAnnotations().get(DeobAnnotations.OBFUSCATED_NAME);
				if (nameAnn == null) {
					continue;
				}
				var mapped = mappings.get(method.getPoolMethod());
				if (mapped == null) {
					mapped = "_" + nameIndex;
					nameIndex++;
				}
				final var virtualMethods = VirtualMethods.getVirtualMethods(method);
				for (Method virtualMethod : virtualMethods) {
					final var deobAnnotation = this.deobfuscated
							.findClass(virtualMethod.getClassFile().getName())
							.findMethod(virtualMethod.getName(), virtualMethod.getDescriptor())
							.getAnnotations().get(DeobAnnotations.OBFUSCATED_NAME);
					deobAnnotation.setElement("value", mapped);
					final var renamedName = deobAnnotation.getValueString();
					mappings.map(virtualMethod.getPoolMethod(), renamedName);
				}
			}
		}
		final var renamer = new Renamer(mappings);
		renamer.run(this.vanilla);

		for (ClassFile classFile : this.deobfuscated) {
			{
				final var annotations = classFile.getAnnotations();
				final var obfuscatedName = annotations.get(DeobAnnotations.OBFUSCATED_NAME);
				if (obfuscatedName != null) {
					obfuscatedName.setElement("value", classFile.getName());
				}
			}
			{
				for (Method method : classFile.getMethods()) {
					final var annotations = method.getAnnotations();
					final var obfuscatedSignature = annotations.get(DeobAnnotations.OBFUSCATED_SIGNATURE);
					if (obfuscatedSignature != null) {
						obfuscatedSignature.setElement("descriptor", method.getDescriptor().toString());
					}
				}
			}
			for (Field field : classFile.getFields()) {
				final var annotations = field.getAnnotations();
				final var obfuscatedName = annotations.get(DeobAnnotations.OBFUSCATED_NAME);
				if (obfuscatedName != null) {
					obfuscatedName.setElement("value", field.getName());
				}
				final var obfuscatedSignature = annotations.get(DeobAnnotations.OBFUSCATED_SIGNATURE);
				if (obfuscatedSignature != null) {
					obfuscatedSignature.setElement("descriptor", field.getType().toString());
				}
				final var obfuscatedGetter = annotations.get(DeobAnnotations.OBFUSCATED_GETTER);
				if (obfuscatedGetter != null) {
					annotations.remove(DeobAnnotations.OBFUSCATED_GETTER);
				}
			}
		}
	}

	public void initToVanilla()
	{
		for (final ClassFile deobClass : deobfuscated)
		{
			if (deobClass.getName().startsWith("net/runelite/") || deobClass.getName().startsWith("netscape"))
			{
				continue;
			}

			final String obName = InjectUtil.getObfuscatedName(deobClass);
			if (obName != null)
			{
				toDeob.put(obName, deobClass);

				final ClassFile obClass = this.vanilla.findClass(obName);

				if (obClass != null)
				{
					toVanilla.put(deobClass, obClass);
				}
			}
		}
	}

	/**
	 * Deobfuscated ClassFile -> Vanilla ClassFile
	 */
	public ClassFile toVanilla(ClassFile deobClass)
	{

		return toVanilla.get(deobClass);
	}

	/**
	 * Deobfuscated Method -> Vanilla Method
	 */
	public Method toVanilla(Method deobMeth)
	{
		final ClassFile obC = toVanilla(deobMeth.getClassFile());

		String name = InjectUtil.getObfuscatedName(deobMeth);

		Signature sig = deobMeth.getObfuscatedSignature();
		if (sig == null)
		{
			sig = deobMeth.getDescriptor();
		}

		return obC.findMethod(name, sig);
	}

	/**
	 * Deobfuscated Field -> Vanilla Field
	 */
	public Field toVanilla(Field deobField)
	{
		final ClassFile obC = toVanilla(deobField.getClassFile());

		String name = InjectUtil.getObfuscatedName(deobField);

		Type type = deobField.getObfuscatedType();

		return obC.findField(name, type);
	}

	/**
	 * Vanilla ClassFile -> Deobfuscated ClassFile
	 */
	public ClassFile toDeob(String str)
	{
		return this.toDeob.get(str);
	}

	/**
	 * Adds a string mapping for a deobfuscated class
	 */
	public void addToDeob(String key, ClassFile value)
	{
		toDeob.put(key, value);
	}

	/**
	 * Do something with all paired classes.
	 * <p>
	 * Key = deobfuscated, Value = vanilla
	 */
	public void forEachPair(BiConsumer<ClassFile, ClassFile> action)
	{
		for (Map.Entry<ClassFile, ClassFile> pair : toVanilla.entrySet())
		{
			action.accept(pair.getKey(), pair.getValue());
		}
	}
}
