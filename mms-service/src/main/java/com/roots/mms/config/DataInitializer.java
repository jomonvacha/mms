package com.roots.mms.config;

import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Entitlement;
import com.roots.mms.entity.Feature;
import com.roots.mms.entity.MembershipCategoryConfig;
import com.roots.mms.entity.MembershipTierConfig;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.MembershipTypeConfig;
import com.roots.mms.entity.RoleFeatureMap;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.TierEntitlement;
import com.roots.mms.entity.User;
import com.roots.mms.repository.AiModelProviderRepository;
import com.roots.mms.repository.AiModelRepository;
import com.roots.mms.repository.AiModelTierBindingRepository;
import com.roots.mms.repository.EntitlementRepository;
import com.roots.mms.repository.FeatureRepository;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipCategoryConfigRepository;
import com.roots.mms.repository.MembershipTierConfigRepository;
import com.roots.mms.repository.MembershipTypeConfigRepository;
import com.roots.mms.repository.RoleFeatureMapRepository;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.repository.SignupInviteCodeRepository;
import com.roots.mms.repository.TierEntitlementRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FeatureRepository featureRepository;
    private final RoleFeatureMapRepository roleFeatureMapRepository;
    private final MembershipTypeConfigRepository membershipTypeConfigRepository;
    private final MembershipCategoryConfigRepository categoryRepository;
    private final MembershipTierConfigRepository tierRepository;
    private final EntitlementRepository entitlementRepository;
    private final TierEntitlementRepository tierEntitlementRepository;
    private final MemberRepository memberRepository;
    private final SignupInviteCodeRepository signupInviteCodeRepository;
    private final AiModelProviderRepository aiModelProviderRepository;
    private final AiModelRepository aiModelRepository;
    private final AiModelTierBindingRepository aiModelTierBindingRepository;

    @Value("${app.admin.enabled:false}")
    private boolean adminEnabled;
    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;
    @Value("${app.admin.password:}")
    private String adminPassword;
    @Value("${app.admin.first-name:System}")
    private String adminFirstName;
    @Value("${app.admin.last-name:Administrator}")
    private String adminLastName;

    @Value("${app.admin.extra-emails:}")
    private String extraAdminEmails; // comma-separated list of admin emails to seed
    @Value("${app.admin.default-password:}")
    private String defaultAdminPassword;

    /**
     * The migration profile sets {@code migration.skip-seeding=true} so the
     * batch tool owns writes end-to-end without the seeder fighting it over
     * roles / features / category rows. Default profile leaves this false
     * (matchIfMissing behaviour) so normal startup keeps seeding.
     */
    @Value("${migration.skip-seeding:false}")
    private boolean migrationSkipSeeding;

    /** Opt-in demo member seeding. Disabled by default — never enable in production. */
    @Value("${app.demo.seed-members:false}")
    private boolean seedDemoMembers;
    @Value("${app.demo.default-password:Password123!}")
    private String demoDefaultPassword;

    private static String capitalizeWord(String s) {
        if (s == null || s.isBlank()) return "Admin";
        String w = s.replace('.', ' ').replace('-', ' ').replace('_', ' ').trim();
        if (w.isEmpty()) return "Admin";
        String[] parts = w.split("\\s+");
        String p = parts[0];
        return p.substring(0, 1).toUpperCase() + p.substring(1);
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (migrationSkipSeeding) {
            log.info("DataInitializer: skipping seed (migration.skip-seeding=true)");
            return;
        }
        initializeRoles();
        migrateLegacyUserRoleToMember();
        cleanupDeprecatedPlatformRoles();
        cleanupDeprecatedIdfyFeatures();
        seedDefaultFeatures();
        seedMembershipCategoriesAndTiers();
        seedEntitlements();
        seedTierEntitlementDefaults();
        seedAiModelRegistry();
        seedMembershipTypes();
        backfillMemberCategoryAndTier();
        seedAdminUserIfConfigured();
        seedExtraAdminEmailsIfConfigured();
        seedDemoMembersIfConfigured();
        seedStarterInviteCode();
    }

    /**
     * Seed a starter signup invite so the IDFY signup flow is usable out of
     * the box. Admins can deactivate this code and issue their own via the
     * admin UI.
     */
    private void seedStarterInviteCode() {
        final String code = "IDFY-WELCOME";
        if (signupInviteCodeRepository.existsByCode(code)) return;
        SignupInviteCode invite = SignupInviteCode.builder()
                .code(code)
                .description("Default IDFY launch invite. Rotate or disable in production.")
                .maxUses(100)
                .usedCount(0)
                .active(true)
                .createdBy("system")
                .createdAt(java.time.Instant.now())
                .build();
        signupInviteCodeRepository.save(invite);
        log.info("Seeded starter signup invite code: {}", code);
    }

    /**
     * Dev-only demo members — one per category/tier combination. Gated behind
     * {@code app.demo.seed-members=true}; do NOT enable in production. Lets
     * local and test environments exercise the full entitlement matrix
     * without manual setup.
     */
    private void seedDemoMembersIfConfigured() {
        if (!seedDemoMembers) return;

        record DemoMember(String username, String email, String first, String last,
                           String categoryCode, String tierCode) {}
        var demos = java.util.List.of(
                new DemoMember("demo_personal_free", "personal.free@demo.local",
                        "Personal", "Free", "PERSONAL", "FREE"),
                new DemoMember("demo_personal_pro", "personal.pro@demo.local",
                        "Personal", "Pro", "PERSONAL", "PRO"),
                new DemoMember("demo_edu_pro", "edu.pro@demo.local",
                        "Education", "Pro", "EDUCATION", "PRO"),
                new DemoMember("demo_ent_pro", "ent.pro@demo.local",
                        "Enterprise", "Pro", "ENTERPRISE", "PRO"),
                new DemoMember("demo_ent_max", "ent.max@demo.local",
                        "Enterprise", "Max", "ENTERPRISE", "MAX")
        );

        Role memberRole = roleRepository.findByName(ERole.ROLE_MEMBER.name()).orElse(null);
        if (memberRole == null) {
            log.warn("[Demo] Skipping demo member seed — ROLE_MEMBER not initialized");
            return;
        }

        for (DemoMember d : demos) {
            if (userRepository.findByEmail(d.email()).isPresent()) continue;
            User u = new User(d.username(), d.email(),
                    passwordEncoder.encode(demoDefaultPassword), d.first(), d.last());
            u.setActive(true);
            u.setEmailVerified(true);
            Set<Role> roles = new HashSet<>();
            roles.add(memberRole);
            u.setRoles(roles);
            User saved = userRepository.save(u);

            // Member with the right category/tier so entitlement resolution returns
            // the tier-specific values on first sign-in.
            var existing = memberRepository.findByUserId(saved.getId());
            if (existing.isEmpty()) {
                String membershipId;
                do { membershipId = "MEM-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
                while (memberRepository.existsByMembershipId(membershipId));

                var summary = new com.roots.mms.entity.UserSummary(
                        saved.getId() != null ? saved.getId().toString() : null,
                        saved.getUsername(), saved.getEmail(),
                        saved.getFirstName(), saved.getLastName(), null, saved.getActive());
                var member = new com.roots.mms.entity.Member(membershipId, summary, MembershipType.BASIC);
                member.setUserId(saved.getId());
                member.setCategoryCode(d.categoryCode());
                member.setTierCode(d.tierCode());
                member.setStatus(com.roots.mms.entity.MembershipStatus.ACTIVE);
                member.setIsActive(true);
                memberRepository.save(member);
            }

            log.info("[Demo] Seeded member {} ({}/{})", d.email(), d.categoryCode(), d.tierCode());
        }
    }

    /**
     * Seed the built-in membership categories (PERSONAL, EDUCATION,
     * ENTERPRISE) and their category-scoped tiers. Re-runs are idempotent:
     * existing records are only updated to keep system flags and display
     * names in sync.
     */
    private void seedMembershipCategoriesAndTiers() {
        record CategoryDef(String code, String displayName, String description, int sortOrder) {}
        record TierDef(String categoryCode, String tierCode, String displayName, String description, int sortOrder) {}

        var categories = java.util.List.of(
                new CategoryDef("PERSONAL", "Personal", "Individual members and prosumers", 10),
                new CategoryDef("EDUCATION", "Education", "Students, teachers, and institutions", 20),
                new CategoryDef("ENTERPRISE", "Enterprise", "Businesses and organisations", 30)
        );
        var tiers = java.util.List.of(
                new TierDef("PERSONAL", "FREE", "Free", "Entry-level personal plan", 10),
                new TierDef("PERSONAL", "PRO", "Pro", "For serious personal use", 20),
                new TierDef("PERSONAL", "MAX", "Max", "Highest personal tier", 30),
                new TierDef("EDUCATION", "FREE", "Free", "Free tier for students and educators", 10),
                new TierDef("EDUCATION", "PRO", "Pro", "Verified educators and institutions", 20),
                new TierDef("ENTERPRISE", "FREE", "Free", "Trial tier for businesses", 10),
                new TierDef("ENTERPRISE", "PRO", "Pro", "Standard enterprise plan", 20),
                new TierDef("ENTERPRISE", "MAX", "Max", "Full enterprise capabilities", 30)
        );

        for (CategoryDef def : categories) {
            var existing = categoryRepository.findByCode(def.code());
            if (existing.isEmpty()) {
                categoryRepository.save(MembershipCategoryConfig.builder()
                        .code(def.code())
                        .displayName(def.displayName())
                        .description(def.description())
                        .enabled(true)
                        .sortOrder(def.sortOrder())
                        .system(true)
                        .build());
                log.info("[Governance] Seeded category {}", def.code());
            } else {
                var cat = existing.get();
                boolean changed = false;
                if (!Boolean.TRUE.equals(cat.getSystem())) { cat.setSystem(true); changed = true; }
                if (cat.getDisplayName() == null || cat.getDisplayName().isBlank()) {
                    cat.setDisplayName(def.displayName()); changed = true;
                }
                if (cat.getDescription() == null || cat.getDescription().isBlank()) {
                    cat.setDescription(def.description()); changed = true;
                }
                if (changed) categoryRepository.save(cat);
            }
        }
        for (TierDef def : tiers) {
            var existing = tierRepository.findByCategoryCodeAndTierCode(def.categoryCode(), def.tierCode());
            if (existing.isEmpty()) {
                tierRepository.save(MembershipTierConfig.builder()
                        .categoryCode(def.categoryCode())
                        .tierCode(def.tierCode())
                        .displayName(def.displayName())
                        .description(def.description())
                        .enabled(true)
                        .sortOrder(def.sortOrder())
                        .system(true)
                        .build());
                log.info("[Governance] Seeded tier {}/{}", def.categoryCode(), def.tierCode());
            } else {
                var tier = existing.get();
                boolean changed = false;
                if (!Boolean.TRUE.equals(tier.getSystem())) { tier.setSystem(true); changed = true; }
                if (tier.getDisplayName() == null || tier.getDisplayName().isBlank()) {
                    tier.setDisplayName(def.displayName()); changed = true;
                }
                if (changed) tierRepository.save(tier);
            }
        }
    }

    /**
     * Seed the system entitlement definitions that IDFY and MMS features use
     * to gate access. Consumers look these up by key — never by enum — so new
     * product surfaces can add entitlements without a code change to the
     * resolver.
     */
    private void seedEntitlements() {
        record EntDef(String key, String displayName, String description,
                       Entitlement.ValueType valueType, String category, String defaultValue) {}

        var defs = java.util.List.of(
                new EntDef("idfy.persona.maxCount",
                        "Max Personas",
                        "Maximum personas a member can own",
                        Entitlement.ValueType.INTEGER, "IDFY Personas", "1"),
                new EntDef("idfy.persona.multiplePersonas",
                        "Multiple Personas",
                        "Allow more than one persona per member",
                        Entitlement.ValueType.BOOLEAN, "IDFY Personas", "false"),
                new EntDef("idfy.persona.advancedAiSettings",
                        "Advanced AI Settings",
                        "Allow fine-tuning strictness, custom refusal and AI instructions",
                        Entitlement.ValueType.BOOLEAN, "IDFY Personas", "false"),
                new EntDef("idfy.persona.advancedModels",
                        "Advanced AI Models",
                        "Access to Gold-tier AI models with higher capability",
                        Entitlement.ValueType.BOOLEAN, "IDFY Personas", "false"),
                new EntDef("idfy.persona.premiumModels",
                        "Premium AI Models",
                        "Access to Platinum-tier AI models",
                        Entitlement.ValueType.BOOLEAN, "IDFY Personas", "false"),
                new EntDef("idfy.persona.share",
                        "Persona Sharing",
                        "Allow generating and managing public invite links",
                        Entitlement.ValueType.BOOLEAN, "IDFY Sharing", "true"),
                new EntDef("idfy.persona.shareMaxTokens",
                        "Max Active Invite Links",
                        "Maximum concurrently active invite tokens per persona",
                        Entitlement.ValueType.INTEGER, "IDFY Sharing", "3"),
                new EntDef("idfy.persona.analytics",
                        "Persona Analytics",
                        "Access to per-persona activity and session analytics",
                        Entitlement.ValueType.BOOLEAN, "IDFY Personas", "true"),
                new EntDef("idfy.api.access",
                        "API Access",
                        "Programmatic access to MMS and IDFY APIs",
                        Entitlement.ValueType.BOOLEAN, "Platform", "false"),

                // ── Knowledge sources (phase 1: file upload) ──────────
                new EntDef("idfy.persona.knowledge.maxSources",
                        "Max Knowledge Sources",
                        "Maximum number of knowledge sources (files) per persona. 0 disables the feature.",
                        Entitlement.ValueType.INTEGER, "IDFY Knowledge", "0"),
                new EntDef("idfy.persona.knowledge.maxBytesPerFile",
                        "Max Bytes Per File",
                        "Largest single knowledge file upload in bytes.",
                        Entitlement.ValueType.INTEGER, "IDFY Knowledge", "10485760"),
                new EntDef("idfy.persona.knowledge.maxTotalBytes",
                        "Max Total Knowledge Bytes",
                        "Aggregate size cap of knowledge sources per persona in bytes.",
                        Entitlement.ValueType.INTEGER, "IDFY Knowledge", "52428800")
        );

        for (EntDef def : defs) {
            var existing = entitlementRepository.findByKey(def.key());
            if (existing.isEmpty()) {
                entitlementRepository.save(Entitlement.builder()
                        .key(def.key())
                        .displayName(def.displayName())
                        .description(def.description())
                        .valueType(def.valueType())
                        .category(def.category())
                        .defaultValue(def.defaultValue())
                        .system(true)
                        .build());
            } else {
                var e = existing.get();
                boolean changed = false;
                if (!Boolean.TRUE.equals(e.getSystem())) { e.setSystem(true); changed = true; }
                if (e.getDisplayName() == null || e.getDisplayName().isBlank()) {
                    e.setDisplayName(def.displayName()); changed = true;
                }
                if (e.getDescription() == null || e.getDescription().isBlank()) {
                    e.setDescription(def.description()); changed = true;
                }
                if (changed) entitlementRepository.save(e);
            }
        }
    }

    /**
     * Seed default tier→entitlement values for the built-in category/tier
     * matrix. Only writes when a value isn't already set — admins can
     * override any of these through the governance API without having their
     * changes clobbered on next startup.
     */
    private void seedTierEntitlementDefaults() {
        record Binding(String category, String tier, String key, String value) {}
        var bindings = java.util.List.of(
                // PERSONAL
                new Binding("PERSONAL", "FREE", "idfy.persona.maxCount", "1"),
                new Binding("PERSONAL", "FREE", "idfy.persona.multiplePersonas", "false"),
                new Binding("PERSONAL", "FREE", "idfy.persona.advancedAiSettings", "false"),
                new Binding("PERSONAL", "FREE", "idfy.persona.advancedModels", "false"),
                new Binding("PERSONAL", "FREE", "idfy.persona.premiumModels", "false"),
                new Binding("PERSONAL", "FREE", "idfy.persona.share", "true"),
                new Binding("PERSONAL", "FREE", "idfy.persona.shareMaxTokens", "3"),
                new Binding("PERSONAL", "FREE", "idfy.persona.analytics", "true"),

                new Binding("PERSONAL", "PRO", "idfy.persona.maxCount", "3"),
                new Binding("PERSONAL", "PRO", "idfy.persona.multiplePersonas", "true"),
                new Binding("PERSONAL", "PRO", "idfy.persona.advancedAiSettings", "true"),
                new Binding("PERSONAL", "PRO", "idfy.persona.advancedModels", "true"),
                new Binding("PERSONAL", "PRO", "idfy.persona.premiumModels", "false"),
                new Binding("PERSONAL", "PRO", "idfy.persona.shareMaxTokens", "10"),

                new Binding("PERSONAL", "MAX", "idfy.persona.maxCount", "10"),
                new Binding("PERSONAL", "MAX", "idfy.persona.multiplePersonas", "true"),
                new Binding("PERSONAL", "MAX", "idfy.persona.advancedAiSettings", "true"),
                new Binding("PERSONAL", "MAX", "idfy.persona.advancedModels", "true"),
                new Binding("PERSONAL", "MAX", "idfy.persona.premiumModels", "true"),
                new Binding("PERSONAL", "MAX", "idfy.persona.shareMaxTokens", "25"),

                // EDUCATION
                new Binding("EDUCATION", "FREE", "idfy.persona.maxCount", "2"),
                new Binding("EDUCATION", "FREE", "idfy.persona.multiplePersonas", "true"),
                new Binding("EDUCATION", "FREE", "idfy.persona.advancedAiSettings", "false"),
                new Binding("EDUCATION", "FREE", "idfy.persona.advancedModels", "false"),
                new Binding("EDUCATION", "FREE", "idfy.persona.shareMaxTokens", "5"),

                new Binding("EDUCATION", "PRO", "idfy.persona.maxCount", "15"),
                new Binding("EDUCATION", "PRO", "idfy.persona.multiplePersonas", "true"),
                new Binding("EDUCATION", "PRO", "idfy.persona.advancedAiSettings", "true"),
                new Binding("EDUCATION", "PRO", "idfy.persona.advancedModels", "true"),
                new Binding("EDUCATION", "PRO", "idfy.persona.premiumModels", "false"),
                new Binding("EDUCATION", "PRO", "idfy.persona.shareMaxTokens", "20"),

                // ENTERPRISE
                new Binding("ENTERPRISE", "FREE", "idfy.persona.maxCount", "2"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.multiplePersonas", "true"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.advancedAiSettings", "true"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.advancedModels", "false"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.premiumModels", "false"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.shareMaxTokens", "10"),

                new Binding("ENTERPRISE", "PRO", "idfy.persona.maxCount", "25"),
                new Binding("ENTERPRISE", "PRO", "idfy.persona.multiplePersonas", "true"),
                new Binding("ENTERPRISE", "PRO", "idfy.persona.advancedAiSettings", "true"),
                new Binding("ENTERPRISE", "PRO", "idfy.persona.advancedModels", "true"),
                new Binding("ENTERPRISE", "PRO", "idfy.persona.premiumModels", "false"),
                new Binding("ENTERPRISE", "PRO", "idfy.persona.shareMaxTokens", "50"),
                new Binding("ENTERPRISE", "PRO", "idfy.api.access", "true"),

                new Binding("ENTERPRISE", "MAX", "idfy.persona.maxCount", "250"),
                new Binding("ENTERPRISE", "MAX", "idfy.persona.multiplePersonas", "true"),
                new Binding("ENTERPRISE", "MAX", "idfy.persona.advancedAiSettings", "true"),
                new Binding("ENTERPRISE", "MAX", "idfy.persona.advancedModels", "true"),
                new Binding("ENTERPRISE", "MAX", "idfy.persona.premiumModels", "true"),
                new Binding("ENTERPRISE", "MAX", "idfy.persona.shareMaxTokens", "500"),
                new Binding("ENTERPRISE", "MAX", "idfy.api.access", "true"),

                // ── Knowledge sources per tier ──────────────────────
                //   Free: 1 file (users see the feature, not a paywall — upgrade
                //   prompt appears at cap, not pre-block).
                //   Pro: 5 files @ 10MB each, 50MB total.
                //   Business/MAX: 15 files @ 20MB, 200MB. Enterprise/MAX: 50 @ 50MB, 1GB.
                new Binding("PERSONAL", "FREE", "idfy.persona.knowledge.maxSources", "1"),
                new Binding("PERSONAL", "PRO",  "idfy.persona.knowledge.maxSources", "5"),
                new Binding("PERSONAL", "PRO",  "idfy.persona.knowledge.maxBytesPerFile", "10485760"),
                new Binding("PERSONAL", "PRO",  "idfy.persona.knowledge.maxTotalBytes",   "52428800"),
                new Binding("PERSONAL", "MAX",  "idfy.persona.knowledge.maxSources", "10"),
                new Binding("PERSONAL", "MAX",  "idfy.persona.knowledge.maxBytesPerFile", "20971520"),
                new Binding("PERSONAL", "MAX",  "idfy.persona.knowledge.maxTotalBytes",   "104857600"),

                new Binding("EDUCATION", "FREE", "idfy.persona.knowledge.maxSources", "1"),
                new Binding("EDUCATION", "PRO",  "idfy.persona.knowledge.maxSources", "15"),
                new Binding("EDUCATION", "PRO",  "idfy.persona.knowledge.maxBytesPerFile", "20971520"),
                new Binding("EDUCATION", "PRO",  "idfy.persona.knowledge.maxTotalBytes",   "209715200"),

                new Binding("ENTERPRISE", "FREE", "idfy.persona.knowledge.maxSources", "3"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.knowledge.maxBytesPerFile", "10485760"),
                new Binding("ENTERPRISE", "FREE", "idfy.persona.knowledge.maxTotalBytes",   "52428800"),
                new Binding("ENTERPRISE", "PRO",  "idfy.persona.knowledge.maxSources", "15"),
                new Binding("ENTERPRISE", "PRO",  "idfy.persona.knowledge.maxBytesPerFile", "20971520"),
                new Binding("ENTERPRISE", "PRO",  "idfy.persona.knowledge.maxTotalBytes",   "209715200"),
                new Binding("ENTERPRISE", "MAX",  "idfy.persona.knowledge.maxSources", "50"),
                new Binding("ENTERPRISE", "MAX",  "idfy.persona.knowledge.maxBytesPerFile", "52428800"),
                new Binding("ENTERPRISE", "MAX",  "idfy.persona.knowledge.maxTotalBytes",   "1073741824")
        );

        int added = 0;
        for (Binding b : bindings) {
            if (tierEntitlementRepository
                    .findByCategoryCodeAndTierCodeAndEntitlementKey(b.category(), b.tier(), b.key()).isEmpty()) {
                tierEntitlementRepository.save(TierEntitlement.builder()
                        .categoryCode(b.category())
                        .tierCode(b.tier())
                        .entitlementKey(b.key())
                        .value(b.value())
                        .build());
                added++;
            }
        }
        if (added > 0) log.info("[Governance] Seeded {} default tier entitlement bindings", added);

        // ── One-time: bump Free-tier knowledge.maxSources from 0 → 1 ──
        // Prior seed set Free to 0, which pre-blocked the feature behind an
        // upsell. Product decision: free users should see one source slot so
        // the upgrade prompt appears at cap, not before. Idempotent — only
        // rewrites values that are still exactly "0".
        int bumped = 0;
        for (String category : java.util.List.of("PERSONAL", "EDUCATION")) {
            var binding = tierEntitlementRepository
                    .findByCategoryCodeAndTierCodeAndEntitlementKey(
                            category, "FREE", "idfy.persona.knowledge.maxSources");
            if (binding.isPresent() && "0".equals(binding.get().getValue())) {
                var b = binding.get();
                b.setValue("1");
                tierEntitlementRepository.save(b);
                bumped++;
            }
        }
        if (bumped > 0) log.info("[Governance] Bumped knowledge.maxSources 0→1 on {} Free-tier binding(s)", bumped);
    }

    /**
     * Seed the AI model registry so Phase 2 ships with parity to the current
     * idfy-service yaml. We insert the OpenAI provider, four GPT-5.4 variants,
     * and a (category, tier) binding matrix that matches what PersonaController
     * computes today from the {@code advancedModels} / {@code premiumModels}
     * booleans.
     *
     * <p>Purely additive and idempotent — admins who edit these rows keep
     * their changes on every subsequent boot.
     */
    private void seedAiModelRegistry() {
        // ── Provider ────────────────────────────────────────────────
        if (!aiModelProviderRepository.existsByCode("openai")) {
            aiModelProviderRepository.save(AiModelProvider.builder()
                    .code("openai")
                    .displayName("OpenAI")
                    .enabled(true)
                    .build());
        }

        // ── Models ──────────────────────────────────────────────────
        record ModelDef(String code, String displayName, String description,
                        int qualityLevel, int costLevel, int sortOrder) {}

        var modelDefs = java.util.List.of(
                new ModelDef("gpt-5.4-nano",
                        "GPT-5.4 Nano",
                        "Fastest, lowest cost — great for quick replies.",
                        1, 1, 10),
                new ModelDef("gpt-5.4-mini",
                        "GPT-5.4 Mini",
                        "Balanced speed and quality. Safe default for most personas.",
                        2, 2, 20),
                new ModelDef("gpt-5.4",
                        "GPT-5.4",
                        "High-quality reasoning for complex conversations.",
                        3, 3, 30),
                new ModelDef("gpt-5.4-pro",
                        "GPT-5.4 Pro",
                        "Frontier model — best quality, highest cost.",
                        4, 4, 40)
        );

        for (ModelDef def : modelDefs) {
            if (!aiModelRepository.existsByCode(def.code())) {
                aiModelRepository.save(AiModel.builder()
                        .code(def.code())
                        .providerCode("openai")
                        .displayName(def.displayName())
                        .description(def.description())
                        .qualityLevel(def.qualityLevel())
                        .costLevel(def.costLevel())
                        .status(AiModel.Status.ENABLED)
                        .sortOrder(def.sortOrder())
                        .build());
            }
        }

        // ── Tier bindings ───────────────────────────────────────────
        // Mirrors the current PersonaController logic:
        //   ADVANCED = { gpt-5.4, gpt-5.4-pro }  → need idfy.persona.advancedModels
        //   PREMIUM  = { gpt-5.4-pro }           → need idfy.persona.premiumModels
        //   STANDARD = { gpt-5.4-nano, gpt-5.4-mini } → everyone
        // The DEFAULT for every tier is gpt-5.4-mini (matches llm.models.default).
        record BindingDef(String category, String tier, String modelCode, boolean isDefault) {}

        var bindingDefs = new java.util.ArrayList<BindingDef>();

        // Tier-to-model map that reproduces today's effective access.
        record TierAccess(String category, String tier, java.util.List<String> models) {}
        var tiers = java.util.List.of(
                new TierAccess("PERSONAL",   "FREE", java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini")),
                new TierAccess("PERSONAL",   "PRO",  java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4")),
                new TierAccess("PERSONAL",   "MAX",  java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4", "gpt-5.4-pro")),
                new TierAccess("EDUCATION",  "FREE", java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini")),
                new TierAccess("EDUCATION",  "PRO",  java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4")),
                new TierAccess("ENTERPRISE", "FREE", java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini")),
                new TierAccess("ENTERPRISE", "PRO",  java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4")),
                new TierAccess("ENTERPRISE", "MAX",  java.util.List.of("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4", "gpt-5.4-pro"))
        );
        for (TierAccess t : tiers) {
            for (String mc : t.models()) {
                bindingDefs.add(new BindingDef(t.category(), t.tier(), mc, "gpt-5.4-mini".equals(mc)));
            }
        }

        int added = 0;
        for (BindingDef b : bindingDefs) {
            var existing = aiModelTierBindingRepository
                    .findByModelCodeAndCategoryCodeAndTierCode(b.modelCode(), b.category(), b.tier());
            if (existing.isEmpty()) {
                aiModelTierBindingRepository.save(AiModelTierBinding.builder()
                        .modelCode(b.modelCode())
                        .categoryCode(b.category())
                        .tierCode(b.tier())
                        .isDefault(b.isDefault())
                        .build());
                added++;
            }
        }
        if (added > 0) {
            log.info("[AiModels] Seeded {} tier binding(s) across {} model(s)", added, modelDefs.size());
        }
    }

    /**
     * Backfill {@code categoryCode} and {@code tierCode} on legacy Member
     * documents. Members without a category are placed on PERSONAL/FREE — the
     * safest default until an admin moves them.
     */
    private void backfillMemberCategoryAndTier() {
        int backfilled = 0;
        for (var m : memberRepository.findAll()) {
            boolean changed = false;
            if (m.getCategoryCode() == null || m.getCategoryCode().isBlank()) {
                m.setCategoryCode("PERSONAL");
                changed = true;
            }
            if (m.getTierCode() == null || m.getTierCode().isBlank()) {
                m.setTierCode("FREE");
                changed = true;
            }
            if (changed) {
                memberRepository.save(m);
                backfilled++;
            }
        }
        if (backfilled > 0) {
            log.info("[Governance] Backfilled category/tier on {} legacy member(s)", backfilled);
        }
    }

    /** Canonical MMS feature set. Each code maps to a real MMS page; tiles on
     *  "My Access" become clickable links via the UI's FEATURE_ROUTES map. */
    private static final java.util.List<Feature> MMS_FEATURES = java.util.List.of(
        Feature.builder().code("view_dashboard").name("View Dashboard").description("Access the main dashboard and statistics").category("General").icon("LayoutDashboard").enabled(true).build(),
        Feature.builder().code("manage_users").name("Manage Users").description("Create, edit, deactivate, and delete users").category("Administration").icon("Users").enabled(true).build(),
        Feature.builder().code("manage_roles").name("Manage Roles").description("Assign and revoke user roles").category("Administration").icon("Shield").enabled(true).build(),
        Feature.builder().code("manage_features").name("Manage Features").description("Configure feature access per role").category("Administration").icon("Settings").enabled(true).build(),
        Feature.builder().code("manage_settings").name("Manage Settings").description("Configure application settings").category("Administration").icon("Settings").enabled(true).build(),
        Feature.builder().code("manage_locale").name("Manage Locale").description("Configure localization and translations").category("Administration").icon("Globe").enabled(true).build(),
        Feature.builder().code("manage_rules").name("Manage Rules").description("Configure platform governance rules").category("Administration").icon("AlertCircle").enabled(true).build(),
        Feature.builder().code("manage_governance").name("Manage Governance").description("Configure membership categories, tiers, and entitlements").category("Administration").icon("Layers").enabled(true).build(),
        Feature.builder().code("manage_invites").name("Manage Invites").description("Issue and revoke signup invite codes").category("Administration").icon("Ticket").enabled(true).build(),
        Feature.builder().code("manage_models").name("Manage AI Models").description("Govern AI model availability per membership category and tier").category("Administration").icon("Sparkles").enabled(true).build()
    );

    /**
     * Canonical role × feature matrix for MMS. Mirrors the RoleProtectedRoute
     * guards in mms-ui/src/routes.tsx so what a user sees matches what they can
     * actually visit. The reconciliation pass enforces this on every boot so
     * DB drift or legacy extras get corrected.
     */
    private static final java.util.Map<String, java.util.Set<String>> MMS_ROLE_FEATURES = java.util.Map.of(
        "ROLE_ADMIN", java.util.Set.of(
            "view_dashboard", "manage_users", "manage_roles", "manage_features",
            "manage_settings", "manage_locale", "manage_rules", "manage_governance",
            "manage_invites", "manage_models"),
        "ROLE_MANAGER", java.util.Set.of(
            "view_dashboard", "manage_users", "manage_roles", "manage_features",
            "manage_governance", "manage_models"),
        "ROLE_MODERATOR", java.util.Set.of(
            "view_dashboard", "manage_users"),
        "ROLE_MEMBER", java.util.Set.of(
            "view_dashboard")
    );

    private void seedDefaultFeatures() {
        // Seed missing features (additive — never overwrites admin-edited rows).
        int addedFeatures = 0;
        for (var f : MMS_FEATURES) {
            if (!featureRepository.existsByCode(f.getCode())) {
                featureRepository.save(f);
                addedFeatures++;
            }
        }
        if (addedFeatures > 0) log.info("Seeded {} MMS features", addedFeatures);

        // Enforce the canonical role × feature matrix on every boot — corrects
        // historical drift (extra codes, missing codes, legacy IDFY leftovers).
        for (var entry : MMS_ROLE_FEATURES.entrySet()) {
            String role = entry.getKey();
            java.util.Set<String> expected = entry.getValue();
            var rfm = roleFeatureMapRepository.findByRole(role)
                    .orElseGet(() -> RoleFeatureMap.builder()
                            .role(role)
                            .featureCodes(new java.util.HashSet<>())
                            .build());
            java.util.Set<String> current = rfm.getFeatureCodes() != null
                    ? new java.util.HashSet<>(rfm.getFeatureCodes())
                    : new java.util.HashSet<>();
            if (!current.equals(expected)) {
                rfm.setFeatureCodes(new java.util.HashSet<>(expected));
                roleFeatureMapRepository.save(rfm);
                log.info("Reconciled {} feature mapping ({} -> {})", role, current.size(), expected.size());
            } else if (rfm.getId() == null) {
                roleFeatureMapRepository.save(rfm);
            }
        }
    }

    /**
     * One-time cleanup of feature codes that no longer map to any real MMS page.
     * Covers both the legacy IDFY-category features (moved to Membership
     * Governance tier entitlements) and the phantom MMS codes that were seeded
     * without a backing UI (view_members, reports, api_access, audit_log,
     * notifications). Deletes the feature documents and strips their codes off
     * role-feature maps. Idempotent after first run.
     */
    private void cleanupDeprecatedIdfyFeatures() {
        var deprecatedCodes = java.util.Set.of(
                "idfy_access", "persona_create", "persona_manage", "persona_invite",
                "persona_analytics", "persona_ai_settings", "multiple_persona",
                "ai_model_gold", "ai_model_platinum",
                "view_members", "manage_members", "view_reports", "export_data",
                "api_access", "view_audit_log", "send_notifications");

        java.util.List<Feature> existing = deprecatedCodes.stream()
                .map(featureRepository::findByCode)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        // Strip codes from every role-feature mapping first.
        int mappingsTouched = 0;
        for (var rfm : roleFeatureMapRepository.findAll()) {
            if (rfm.getFeatureCodes() != null && rfm.getFeatureCodes().removeAll(deprecatedCodes)) {
                roleFeatureMapRepository.save(rfm);
                mappingsTouched++;
            }
        }

        if (!existing.isEmpty()) {
            featureRepository.deleteAll(existing);
        }

        if (!existing.isEmpty() || mappingsTouched > 0) {
            log.info("Cleaned up {} deprecated IDFY feature(s); pruned from {} role mapping(s)",
                    existing.size(), mappingsTouched);
        }
    }

    private void initializeRoles() {
        var roleMetadata = java.util.Map.of(
                ERole.ROLE_MEMBER, new String[]{"Member", "Standard business-facing platform user"},
                ERole.ROLE_MODERATOR, new String[]{"Moderator", "Content review and community management"},
                ERole.ROLE_MANAGER, new String[]{"Manager", "Team and resource management"},
                ERole.ROLE_ADMIN, new String[]{"Administrator", "Full platform administration"}
        );

        for (ERole eRole : ERole.values()) {
            var meta = roleMetadata.getOrDefault(eRole, new String[]{eRole.name(), ""});
            var existing = roleRepository.findByName(eRole.name());
            if (existing.isEmpty()) {
                Role role = new Role(eRole);
                role.setCategory("System");
                role.setDisplayName(meta[0]);
                role.setDescription(meta[1]);
                roleRepository.save(role);
                log.info("Created role: {}", eRole.name());
            } else {
                Role role = existing.get();
                boolean changed = false;
                if (!Boolean.TRUE.equals(role.getSystem())) {
                    role.setSystem(true);
                    changed = true;
                }
                if (!"System".equals(role.getCategory())) {
                    role.setCategory("System");
                    changed = true;
                }
                if (role.getDisplayName() == null || role.getDisplayName().isBlank() || role.getDisplayName().equals(role.getName())) {
                    role.setDisplayName(meta[0]);
                    changed = true;
                }
                if (role.getDescription() == null || role.getDescription().isBlank()) {
                    role.setDescription(meta[1]);
                    changed = true;
                }
                if (changed) {
                    roleRepository.save(role);
                }
            }
        }
    }

    /**
     * Migrate legacy ROLE_USER records to ROLE_MEMBER.
     *
     * MEMBER is now the standard business-facing user concept. USER is not a
     * supported role. Any user documents that still reference the old
     * ROLE_USER have their role set swapped for ROLE_MEMBER, and the
     * deprecated role document plus its feature mapping are removed.
     */
    private void migrateLegacyUserRoleToMember() {
        var legacyUserRole = roleRepository.findByName("ROLE_USER");
        if (legacyUserRole.isEmpty()) return; // Already cleaned up

        var memberRole = roleRepository.findByName("ROLE_MEMBER")
                .orElseGet(() -> {
                    Role r = new Role(ERole.ROLE_MEMBER);
                    r.setCategory("System");
                    r.setDisplayName("Member");
                    r.setDescription("Standard business-facing platform user");
                    return roleRepository.save(r);
                });

        int migrated = 0;
        for (var user : userRepository.findAll()) {
            boolean hasLegacy = user.getRoles().stream().anyMatch(r -> "ROLE_USER".equals(r.getName()));
            if (hasLegacy) {
                user.getRoles().removeIf(r -> "ROLE_USER".equals(r.getName()));
                user.getRoles().add(memberRole);
                userRepository.save(user);
                migrated++;
            }
        }

        roleFeatureMapRepository.findByRole("ROLE_USER").ifPresent(roleFeatureMapRepository::delete);
        roleRepository.delete(legacyUserRole.get());

        if (migrated > 0) {
            log.info("Migrated {} user(s) from legacy ROLE_USER to ROLE_MEMBER", migrated);
        }
        log.info("Removed deprecated ROLE_USER role");
    }

    /**
     * One-time cleanup of deprecated Platform Access roles. The
     * {@code ROLE_BASIC / ROLE_PREMIUM / ROLE_ENTERPRISE} roles used to gate
     * tier-level feature access. That responsibility now belongs to Membership
     * Governance (category + tier entitlements), so the roles are dead weight.
     * This method strips them from any user that still references them, drops
     * their role-feature mappings, and deletes the role documents. Idempotent —
     * re-running is a no-op once the DB is clean.
     */
    private void cleanupDeprecatedPlatformRoles() {
        var deprecatedNames = java.util.List.of("ROLE_BASIC", "ROLE_PREMIUM", "ROLE_ENTERPRISE");

        java.util.List<Role> existing = deprecatedNames.stream()
                .map(roleRepository::findByName)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (existing.isEmpty()) return; // already clean

        int usersStripped = 0;
        for (var user : userRepository.findAll()) {
            boolean changed = user.getRoles().removeIf(r -> deprecatedNames.contains(r.getName()));
            if (changed) {
                userRepository.save(user);
                usersStripped++;
            }
        }

        for (var name : deprecatedNames) {
            roleFeatureMapRepository.findByRole(name).ifPresent(roleFeatureMapRepository::delete);
        }
        roleRepository.deleteAll(existing);

        log.info("Cleaned up {} deprecated Platform role(s); stripped from {} user(s)",
                existing.size(), usersStripped);
    }

    /**
     * Seed legacy membership types. Superseded by Membership Governance
     * (category + tier entitlements). Kept for backward compatibility with
     * existing member records that still carry a {@code membershipType}; do
     * not remove until those records are migrated off the field.
     */
    private void seedMembershipTypes() {
        if (membershipTypeConfigRepository.count() > 0) {
            // Ensure BASIC=system, others=custom (backfill for existing data)
            membershipTypeConfigRepository.findAll().forEach(mt -> {
                boolean shouldBeSystem = "BASIC".equals(mt.getCode());
                if (shouldBeSystem != Boolean.TRUE.equals(mt.getSystem())) {
                    mt.setSystem(shouldBeSystem);
                    if (shouldBeSystem && (mt.getDescription() == null || mt.getDescription().isBlank())) {
                        mt.setDescription("Default membership for all users");
                    }
                    membershipTypeConfigRepository.save(mt);
                }
            });
            return;
        }
        for (MembershipType mt : MembershipType.values()) {
            boolean isBasic = mt == MembershipType.BASIC;
            membershipTypeConfigRepository.save(MembershipTypeConfig.builder()
                    .code(mt.name())
                    .displayName(mt.name().charAt(0) + mt.name().substring(1).toLowerCase())
                    .description(isBasic ? "Default membership for all users" : "")
                    .system(isBasic)
                    .build());
        }
        log.info("Seeded {} membership types (BASIC=system, rest=custom)", MembershipType.values().length);
    }

    private void seedAdminUserIfConfigured() {
        if (!adminEnabled) return;

        Optional<User> existingByUsername = userRepository.findByUsername(adminUsername);
        Optional<User> existingByEmail = userRepository.findByEmail(adminEmail);
        if (existingByUsername.isPresent() || existingByEmail.isPresent()) {
            log.info("Admin user already present. Skipping seed.");
            return;
        }

        // ADMIN_PASSWORD is mandatory when admin seeding is enabled. Previous
        // behaviour silently fell back to "admin123", which meant production
        // images could ship with a known password if the operator forgot to
        // set one. Refuse to create the admin instead.
        if (adminPassword == null || adminPassword.isBlank()) {
            log.error("app.admin.enabled=true but ADMIN_PASSWORD is not set. "
                    + "Admin user will NOT be created. Set ADMIN_PASSWORD in the environment "
                    + "(or disable admin seeding with ADMIN_ENABLED=false).");
            return;
        }

        User admin = new User(adminUsername, adminEmail,
                passwordEncoder.encode(adminPassword), adminFirstName, adminLastName);
        admin.setActive(true);

        Set<Role> roles = new HashSet<>();
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not initialized"));
        Role memberRole = roleRepository.findByName(ERole.ROLE_MEMBER.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_MEMBER not initialized"));
        roles.add(adminRole);
        roles.add(memberRole);
        admin.setRoles(roles);

        userRepository.save(admin);
        log.info("Seeded default admin user: {}", adminUsername);
    }

    private void seedExtraAdminEmailsIfConfigured() {
        if (extraAdminEmails == null || extraAdminEmails.isBlank()) return;
        // New users need an initial password. Same rule as seedAdminUserIfConfigured:
        // don't fall back to a known default.
        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            log.error("ADMIN_EXTRA_EMAILS set but ADMIN_DEFAULT_PASSWORD is empty. "
                    + "Skipping extra-admin seeding. Set ADMIN_DEFAULT_PASSWORD to create them.");
            return;
        }
        String[] emails = extraAdminEmails.split(",");
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not initialized"));
        Role memberRole = roleRepository.findByName(ERole.ROLE_MEMBER.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_MEMBER not initialized"));
        for (String raw : emails) {
            String email = raw.trim().toLowerCase();
            if (email.isEmpty()) continue;
            if (userRepository.findByEmail(email).isPresent()) {
                // ensure admin role present
                userRepository.findByEmail(email).ifPresent(u -> {
                    if (u.getRoles().stream().noneMatch(r -> "ROLE_ADMIN".equals(r.getName()))) {
                        Set<Role> roles = new java.util.HashSet<>(u.getRoles());
                        roles.add(adminRole);
                        roles.add(memberRole);
                        u.setRoles(roles);
                        userRepository.save(u);
                        log.info("Granted admin role to existing user: {}", email);
                    }
                });
                continue;
            }
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            String baseUsername = local.replaceAll("[^a-zA-Z0-9._-]", "");
            String username = baseUsername.isBlank() ? "admin" : baseUsername;
            // ensure unique username
            String finalUsername = username;
            int counter = 1;
            while (userRepository.findByUsername(finalUsername).isPresent()) {
                finalUsername = username + counter++;
            }
            String firstName = capitalizeWord(local);
            String lastName = "Admin";
            User admin = new User(finalUsername, email,
                    passwordEncoder.encode(defaultAdminPassword), firstName, lastName);
            admin.setActive(true);
            Set<Role> roles = new java.util.HashSet<>();
            roles.add(adminRole);
            roles.add(memberRole);
            admin.setRoles(roles);
            userRepository.save(admin);
            log.info("Seeded extra admin user: {} ({})", finalUsername, email);
        }
    }
}
