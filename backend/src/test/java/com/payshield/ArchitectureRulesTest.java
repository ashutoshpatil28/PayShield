package com.payshield;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.payshield", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule commonDoesNotDependOnBusinessModules =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..auth..", "..merchant..", "..audit..", "..payment..",
                            "..provider..", "..webhook..", "..ledger..", "..reconciliation..",
                            "..payout..", "..risk..", "..outbox..", "..ops.."
                    );

    @ArchTest
    static final ArchRule auditDoesNotDependOnBusinessModules =
            noClasses().that().resideInAPackage("..audit..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..auth..", "..merchant..", "..payment..",
                            "..provider..", "..webhook..", "..ledger..", "..reconciliation..",
                            "..payout..", "..risk..", "..outbox..", "..ops.."
                    );
}
