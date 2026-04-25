package org.example.agentaiops.repair.extension;

import org.springframework.stereotype.Component;

@Component
public class ManualRepairTrigger implements RepairTrigger {

    @Override
    public String name() {
        return "manual";
    }
}
