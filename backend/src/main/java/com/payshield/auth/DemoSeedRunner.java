package com.payshield.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds local demonstration accounts for each RBAC role.
 * Never enabled in production environments.
 */
@Component
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);
    public static final String DEMO_PASSWORD = "PayShield@Demo2026!";

    private static final UUID ACME_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID NOVA_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222202");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDemoData;

    public DemoSeedRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${payshield.security.seed-demo-data:true}") boolean seedDemoData
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedDemoData = seedDemoData;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            return;
        }
        seedUser("33333333-3333-3333-3333-333333333301", "admin@payshield.local", "Aarav Sharma (Platform Admin)", null, RoleName.PLATFORM_ADMIN);
        seedUser("33333333-3333-3333-3333-333333333302", "acme.merchant@payshield.local", "Priya Nair (Acme Merchant Admin)", ACME_MERCHANT_ID, RoleName.MERCHANT_ADMIN);
        seedUser("33333333-3333-3333-3333-333333333303", "nova.merchant@payshield.local", "Rohan Verma (Nova Merchant Admin)", NOVA_MERCHANT_ID, RoleName.MERCHANT_ADMIN);
        seedUser("33333333-3333-3333-3333-333333333304", "ops@payshield.local", "Meera Iyer (Operations Analyst)", null, RoleName.OPERATIONS_ANALYST);
        seedUser("33333333-3333-3333-3333-333333333305", "risk@payshield.local", "Kabir Patel (Risk Analyst)", null, RoleName.RISK_ANALYST);
        seedUser("33333333-3333-3333-3333-333333333306", "maker@payshield.local", "Neha Kulkarni (Payout Maker)", ACME_MERCHANT_ID, RoleName.PAYOUT_MAKER);
        seedUser("33333333-3333-3333-3333-333333333307", "checker@payshield.local", "Vikram Desai (Payout Checker)", ACME_MERCHANT_ID, RoleName.PAYOUT_CHECKER);
        seedUser("33333333-3333-3333-3333-333333333308", "auditor@payshield.local", "Ananya Rao (Financial Auditor)", null, RoleName.FINANCIAL_AUDITOR);
        log.info("PayShield demo users verified/seeded for local simulation.");
    }

    private void seedUser(String id, String email, String fullName, UUID merchantId, RoleName roleName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        RoleEntity role = roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleName));
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                UUID.fromString(id),
                email,
                fullName,
                passwordEncoder.encode(DEMO_PASSWORD),
                merchantId,
                true,
                true,
                now,
                now
        );
        user.setRoles(Set.of(role));
        userRepository.save(user);
    }
}
