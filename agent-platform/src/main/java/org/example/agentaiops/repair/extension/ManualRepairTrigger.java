package org.example.agentaiops.repair.extension;

import org.springframework.stereotype.Component;

@Component
public class ManualRepairTrigger implements RepairTrigger {

    /** Identifies repair runs started by the API. */
    @Override
    public String name() {
        return "manual";
    }
}
