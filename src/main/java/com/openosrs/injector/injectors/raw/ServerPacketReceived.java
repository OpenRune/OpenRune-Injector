package com.openosrs.injector.injectors.raw;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injectors.AbstractInjector;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.InstructionType;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.Label;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.AStore;
import net.runelite.asm.attributes.code.instructions.Dup;
import net.runelite.asm.attributes.code.instructions.IConst_1;
import net.runelite.asm.attributes.code.instructions.IfEq;
import net.runelite.asm.attributes.code.instructions.InvokeSpecial;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.InvokeVirtual;
import net.runelite.asm.attributes.code.instructions.New;
import net.runelite.asm.attributes.code.instructions.PutField;
import net.runelite.asm.attributes.code.instructions.Return;
import net.runelite.asm.pool.Class;
import net.runelite.asm.signature.Signature;

import java.util.ListIterator;

public class ServerPacketReceived extends AbstractInjector {

    public ServerPacketReceived(InjectData inject) {
        super(inject);
    }

    @Override
    public void inject() {
        ClassFile clientVanilla = inject.toVanilla(
                inject.getDeobfuscated()
                        .findClass("Client")
        );

        Method serverPacketReceiveMethod = InjectUtil.findMethod(inject, "method1434", "Client");
        Field insertionPoint = InjectUtil.findField(inject, "field1423", "PacketWriter");

        Instructions ins = serverPacketReceiveMethod.getCode().getInstructions();

        ListIterator<Instruction> iterator = ins.getInstructions().listIterator();
        int idx = -1;
        while (iterator.hasNext()) {
            Instruction i = iterator.next();
            if (!(i instanceof PutField)) {
                //System.out.println("Instruction: "+ i);
                continue;
            }
            if (((PutField) i).getField().getName().equals(insertionPoint.getName())) {
                idx = ins.getInstructions().indexOf(i) + 1;
                break;
            }
        }

        if (idx == -1)
        {
            System.out.println("Couldn't find insertion point for ServerPacketReceived");
            //System.exit(0);
            return;
        }

        ins.addInstruction(idx++, new Label(ins));
        ins.addInstruction(idx++, new New(ins, new Class("net/unethicalite/api/events/ServerPacketReceived")));
        ins.addInstruction(idx++, new Dup(ins));
        ins.addInstruction(idx++, new InvokeSpecial(ins, new net.runelite.asm.pool.Method(new Class("net/unethicalite/api/events/ServerPacketReceived"), "<init>", new Signature("()V"))));
        ins.addInstruction(idx++, new AStore(ins, 7));

        ins.addInstruction(idx++, new Label(ins));
        ins.addInstruction(idx++, new ALoad(ins, 7));
        ins.addInstruction(idx++, new InvokeStatic(ins, new net.runelite.asm.pool.Method(clientVanilla.getPoolClass(), "onServerPacketReceived", new Signature("(Lnet/unethicalite/api/events/ServerPacketReceived;)V"))));

        ins.addInstruction(idx++, new Label(ins));
        ins.addInstruction(idx++, new ALoad(ins, 7));
        ins.addInstruction(idx++, new InvokeVirtual(ins, new net.runelite.asm.pool.Method(new Class("net/unethicalite/api/events/ServerPacketReceived"), "isConsumed", new Signature("()Z"))));
        Label jump = new Label(ins); // We will jump here if not consumed
        ins.addInstruction(idx++, new IfEq(ins, jump)); // If not consumed

        ins.addInstruction(idx++, new Label(ins));
        ins.addInstruction(idx++, new IConst_1(ins));

        ins.addInstruction(idx++, new Label(ins));
        ins.addInstruction(idx++, new Return(ins, InstructionType.IRETURN));
        ins.addInstruction(idx, jump);
    }
}
