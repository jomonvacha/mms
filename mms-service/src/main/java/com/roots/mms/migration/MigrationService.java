package com.roots.mms.migration;

import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelAudit;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.entity.AppSetting;
import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.EnforcementRule;
import com.roots.mms.entity.Entitlement;
import com.roots.mms.entity.Feature;
import com.roots.mms.entity.LocaleOption;
import com.roots.mms.entity.Member;
import com.roots.mms.entity.MembershipCategoryConfig;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipTierConfig;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.MembershipTypeConfig;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.RoleFeatureMap;
import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.entity.TierEntitlement;
import com.roots.mms.entity.User;
import com.roots.mms.entity.UserAvatar;
import com.roots.mms.entity.UserPreferences;
import com.roots.mms.entity.UserSummary;
import com.roots.mms.entity.VerificationToken;
import com.roots.mms.repository.AiModelAuditRepository;
import com.roots.mms.repository.AiModelProviderRepository;
import com.roots.mms.repository.AiModelRepository;
import com.roots.mms.repository.AiModelTierBindingRepository;
import com.roots.mms.repository.AppSettingRepository;
import com.roots.mms.repository.EnforcementRuleRepository;
import com.roots.mms.repository.EntitlementRepository;
import com.roots.mms.repository.FeatureRepository;
import com.roots.mms.repository.LocaleOptionRepository;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipCategoryConfigRepository;
import com.roots.mms.repository.MembershipTierConfigRepository;
import com.roots.mms.repository.MembershipTypeConfigRepository;
import com.roots.mms.repository.RoleFeatureMapRepository;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.SignupInviteCodeRepository;
import com.roots.mms.repository.TierEntitlementRepository;
import com.roots.mms.repository.UserAvatarRepository;
import com.roots.mms.repository.UserPreferencesRepository;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.repository.VerificationTokenRepository;
import org.bson.Document;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Core of the Mongo → Postgres migration tool. Active only under the
 * {@code migration} Spring profile. Holds one shared {@link IdMap} for the
 * lifetime of the run so FK references stay internally consistent across all
 * per-collection copy passes.
 *
 * <p><b>Execution order matters.</b> Collections with no inbound refs are
 * migrated first; {@code users} is migrated before any table with a
 * {@code user_id} FK. See the {@code copy*} method call list in
 * {@link #runAll(Mode)}.
 *
 * <p><b>Idempotency stance.</b> Each {@code copy*} method checks a business
 * key (username, email, code, name, token) before writing. If the row exists,
 * the document is skipped and counted as a "skipped-existing". A re-run on a
 * partially-migrated target is therefore safe.
 *
 * <p><b>Failure stance.</b> Per-document failures are caught and logged; the
 * run continues. Final exit code is non-zero if any collection had at least
 * one failure.
 */
@Service
@Profile("migration")
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    // ── Mongo collection names (stable — documented in 01-analysis.md) ────────
    private static final String C_ROLES                = "roles";
    private static final String C_FEATURES             = "features";
    private static final String C_ENTITLEMENTS         = "entitlements";
    private static final String C_MEMBERSHIP_CATEGORIES = "membership_categories";
    private static final String C_MEMBERSHIP_TIERS     = "membership_tiers";
    private static final String C_MEMBERSHIP_TYPES     = "membership_types";
    private static final String C_AI_PROVIDERS         = "ai_model_providers";
    private static final String C_AI_MODELS            = "ai_models";
    private static final String C_AI_TIER_BINDINGS     = "ai_model_tier_bindings";
    private static final String C_TIER_ENTITLEMENTS    = "tier_entitlements";
    private static final String C_LOCALE_OPTIONS       = "locale_options";
    private static final String C_APP_SETTINGS         = "app_settings";
    private static final String C_ENFORCEMENT_RULES    = "enforcement_rules";
    private static final String C_SIGNUP_INVITE_CODES  = "signup_invite_codes";
    private static final String C_USERS                = "users";
    private static final String C_ROLE_FEATURES        = "role_features";
    private static final String C_MEMBERS              = "members";
    private static final String C_USER_PREFERENCES     = "user_preferences";
    private static final String C_USER_AVATARS         = "user_avatars";
    private static final String C_VERIFICATION_TOKENS  = "verification_tokens";
    private static final String C_AI_MODEL_AUDIT       = "ai_model_audit";

    /** Order matches {@link #runAll(Mode)} — used only for the final summary. */
    private static final List<String> ALL_COLLECTIONS = List.of(
            C_ROLES, C_FEATURES, C_ENTITLEMENTS,
            C_MEMBERSHIP_CATEGORIES, C_MEMBERSHIP_TIERS, C_MEMBERSHIP_TYPES,
            C_AI_PROVIDERS, C_AI_MODELS, C_AI_TIER_BINDINGS,
            C_TIER_ENTITLEMENTS, C_LOCALE_OPTIONS, C_APP_SETTINGS,
            C_ENFORCEMENT_RULES, C_SIGNUP_INVITE_CODES,
            C_USERS, C_ROLE_FEATURES, C_MEMBERS,
            C_USER_PREFERENCES, C_USER_AVATARS,
            C_VERIFICATION_TOKENS, C_AI_MODEL_AUDIT);

    private final MongoTemplate mongo;
    private final MigrationProperties properties;
    private final IdMap idMap = new IdMap();

    // Direct JPA + Hibernate access for the data copy path.
    //
    // JPA's normal save-path is a dead end here: repo.save() routes to merge()
    // when @Id is set (StaleObjectStateException on empty target), and em.persist()
    // refuses pre-set ids on @GeneratedValue entities ("detached entity passed
    // to persist").
    //
    // Hibernate's StatelessSession.insert() is the escape hatch — it's designed
    // for bulk data copies, inserts with the caller-assigned id, and skips the
    // persistence-context bookkeeping that fires those checks. No cascade, no
    // dirty-check — which is fine because our migrator already decomposes
    // @ManyToMany / @ElementCollection into their own copy passes
    // (copyUserRoles, copyRoleFeatures).
    @PersistenceContext
    private EntityManager em;
    @PersistenceUnit
    private EntityManagerFactory emf;

    // Repositories (one per target table)
    private final RoleRepository roleRepo;
    private final FeatureRepository featureRepo;
    private final EntitlementRepository entitlementRepo;
    private final MembershipCategoryConfigRepository categoryRepo;
    private final MembershipTierConfigRepository tierRepo;
    private final MembershipTypeConfigRepository typeRepo;
    private final AiModelProviderRepository providerRepo;
    private final AiModelRepository modelRepo;
    private final AiModelTierBindingRepository bindingRepo;
    private final TierEntitlementRepository tierEntitlementRepo;
    private final LocaleOptionRepository localeRepo;
    private final AppSettingRepository settingRepo;
    private final EnforcementRuleRepository enforcementRepo;
    private final SignupInviteCodeRepository inviteRepo;
    private final UserRepository userRepo;
    private final RoleFeatureMapRepository roleFeatureMapRepo;
    private final MemberRepository memberRepo;
    private final UserPreferencesRepository prefsRepo;
    private final UserAvatarRepository avatarRepo;
    private final VerificationTokenRepository tokenRepo;
    private final AiModelAuditRepository auditRepo;

    public MigrationService(MongoTemplate mongo,
                            MigrationProperties properties,
                            RoleRepository roleRepo,
                            FeatureRepository featureRepo,
                            EntitlementRepository entitlementRepo,
                            MembershipCategoryConfigRepository categoryRepo,
                            MembershipTierConfigRepository tierRepo,
                            MembershipTypeConfigRepository typeRepo,
                            AiModelProviderRepository providerRepo,
                            AiModelRepository modelRepo,
                            AiModelTierBindingRepository bindingRepo,
                            TierEntitlementRepository tierEntitlementRepo,
                            LocaleOptionRepository localeRepo,
                            AppSettingRepository settingRepo,
                            EnforcementRuleRepository enforcementRepo,
                            SignupInviteCodeRepository inviteRepo,
                            UserRepository userRepo,
                            RoleFeatureMapRepository roleFeatureMapRepo,
                            MemberRepository memberRepo,
                            UserPreferencesRepository prefsRepo,
                            UserAvatarRepository avatarRepo,
                            VerificationTokenRepository tokenRepo,
                            AiModelAuditRepository auditRepo) {
        this.mongo = mongo;
        this.properties = properties;
        this.roleRepo = roleRepo;
        this.featureRepo = featureRepo;
        this.entitlementRepo = entitlementRepo;
        this.categoryRepo = categoryRepo;
        this.tierRepo = tierRepo;
        this.typeRepo = typeRepo;
        this.providerRepo = providerRepo;
        this.modelRepo = modelRepo;
        this.bindingRepo = bindingRepo;
        this.tierEntitlementRepo = tierEntitlementRepo;
        this.localeRepo = localeRepo;
        this.settingRepo = settingRepo;
        this.enforcementRepo = enforcementRepo;
        this.inviteRepo = inviteRepo;
        this.userRepo = userRepo;
        this.roleFeatureMapRepo = roleFeatureMapRepo;
        this.memberRepo = memberRepo;
        this.prefsRepo = prefsRepo;
        this.avatarRepo = avatarRepo;
        this.tokenRepo = tokenRepo;
        this.auditRepo = auditRepo;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Public entry points
    // ══════════════════════════════════════════════════════════════════════════

    public RunSummary dryRun() { return runAll(Mode.DRY_RUN); }
    public RunSummary migrate() { return runAll(Mode.MIGRATE); }
    public RunSummary verify()  { return runAll(Mode.VERIFY); }

    private RunSummary runAll(Mode mode) {
        RunSummary summary = new RunSummary(mode);

        // Independent — no inbound FKs that depend on other migrated tables.
        copyRoles(mode, summary);
        copyFeatures(mode, summary);
        copyEntitlements(mode, summary);
        copyMembershipCategories(mode, summary);
        copyMembershipTiers(mode, summary);
        copyMembershipTypes(mode, summary);
        copyAiProviders(mode, summary);
        copyAiModels(mode, summary);
        copyAiTierBindings(mode, summary);
        copyTierEntitlements(mode, summary);
        copyLocaleOptions(mode, summary);
        copyAppSettings(mode, summary);
        copyEnforcementRules(mode, summary);
        copySignupInviteCodes(mode, summary);

        // Users and user-dependent collections.
        copyUsers(mode, summary);
        copyUserRoles(mode, summary);       // User.roles @DBRef set → user_roles join
        copyRoleFeatures(mode, summary);
        copyMembers(mode, summary);
        copyUserPreferences(mode, summary);
        copyUserAvatars(mode, summary);
        copyVerificationTokens(mode, summary);
        copyAiModelAudit(mode, summary);

        return summary;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Per-collection copy methods
    // ══════════════════════════════════════════════════════════════════════════

    private void copyRoles(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_ROLES, mode, summary);
        List<Role> batch = new ArrayList<>();
        forEachDoc(C_ROLES, stats, doc -> {
            String mongoId = readId(doc);
            String name = doc.getString("name");
            if (name == null || name.isBlank()) { stats.validationError("missing name"); return null; }
            Optional<Role> existing = roleRepo.findByName(name);
            if (existing.isPresent()) {
                // Re-point IdMap at the already-migrated row's UUID so user_roles'
                // DBRef resolution can translate mongo role ids → the real
                // Postgres UUID. Without this, copyUserRoles silently links
                // nothing and migrated users lose their roles.
                idMap.put(C_ROLES, mongoId, existing.get().getId());
                stats.skippedExisting();
                return null;
            }

            Role r = new Role();
            r.setId(idMap.resolveOrAssign(C_ROLES, mongoId));
            r.setName(name);
            r.setDisplayName(doc.getString("displayName"));
            r.setDescription(doc.getString("description"));
            r.setCategory(Optional.ofNullable(doc.getString("category")).orElse("System"));
            r.setSystem(Boolean.TRUE.equals(doc.getBoolean("system")));
            return r;
        }, batch, () -> flushBatch(mode, stats, batch, roleRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_ROLES,
                name -> roleRepo.findByName(name).isPresent(),
                "name");
    }

    private void copyFeatures(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_FEATURES, mode, summary);
        List<Feature> batch = new ArrayList<>();
        forEachDoc(C_FEATURES, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            if (code == null || code.isBlank()) { stats.validationError("missing code"); return null; }
            if (featureRepo.existsByCode(code)) { stats.skippedExisting(); return null; }

            return Feature.builder()
                    .id(idMap.resolveOrAssign(C_FEATURES, mongoId))
                    .code(code)
                    .name(doc.getString("name"))
                    .description(doc.getString("description"))
                    .category(doc.getString("category"))
                    .icon(doc.getString("icon"))
                    .enabled(Boolean.TRUE.equals(doc.getBoolean("enabled", true)))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, featureRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_FEATURES,
                code -> featureRepo.existsByCode(code), "code");
    }

    private void copyEntitlements(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_ENTITLEMENTS, mode, summary);
        List<Entitlement> batch = new ArrayList<>();
        forEachDoc(C_ENTITLEMENTS, stats, doc -> {
            String mongoId = readId(doc);
            String key = doc.getString("key");
            if (key == null || key.isBlank()) { stats.validationError("missing key"); return null; }
            if (entitlementRepo.existsByKey(key)) { stats.skippedExisting(); return null; }

            Entitlement.ValueType valueType = parseEnum(doc.getString("valueType"),
                    Entitlement.ValueType.class, Entitlement.ValueType.BOOLEAN, stats);
            return Entitlement.builder()
                    .id(idMap.resolveOrAssign(C_ENTITLEMENTS, mongoId))
                    .key(key)
                    .displayName(doc.getString("displayName"))
                    .description(doc.getString("description"))
                    .valueType(valueType)
                    .category(doc.getString("category"))
                    .defaultValue(Optional.ofNullable(doc.getString("defaultValue")).orElse("false"))
                    .system(Boolean.TRUE.equals(doc.getBoolean("system")))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, entitlementRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_ENTITLEMENTS,
                key -> entitlementRepo.existsByKey(key), "key");
    }

    private void copyMembershipCategories(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_MEMBERSHIP_CATEGORIES, mode, summary);
        List<MembershipCategoryConfig> batch = new ArrayList<>();
        forEachDoc(C_MEMBERSHIP_CATEGORIES, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            if (code == null || code.isBlank()) { stats.validationError("missing code"); return null; }
            if (categoryRepo.existsByCode(code)) { stats.skippedExisting(); return null; }

            return MembershipCategoryConfig.builder()
                    .id(idMap.resolveOrAssign(C_MEMBERSHIP_CATEGORIES, mongoId))
                    .code(code)
                    .displayName(doc.getString("displayName"))
                    .description(doc.getString("description"))
                    .enabled(Boolean.TRUE.equals(doc.getBoolean("enabled", true)))
                    .sortOrder(intOr(doc, "sortOrder", 0))
                    .system(Boolean.TRUE.equals(doc.getBoolean("system")))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, categoryRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_MEMBERSHIP_CATEGORIES,
                code -> categoryRepo.existsByCode(code), "code");
    }

    private void copyMembershipTiers(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_MEMBERSHIP_TIERS, mode, summary);
        List<MembershipTierConfig> batch = new ArrayList<>();
        forEachDoc(C_MEMBERSHIP_TIERS, stats, doc -> {
            String mongoId = readId(doc);
            String category = doc.getString("categoryCode");
            String tier = doc.getString("tierCode");
            if (category == null || tier == null) { stats.validationError("missing categoryCode/tierCode"); return null; }
            if (tierRepo.existsByCategoryCodeAndTierCode(category, tier)) {
                stats.skippedExisting();
                return null;
            }

            return MembershipTierConfig.builder()
                    .id(idMap.resolveOrAssign(C_MEMBERSHIP_TIERS, mongoId))
                    .categoryCode(category)
                    .tierCode(tier)
                    .displayName(doc.getString("displayName"))
                    .description(doc.getString("description"))
                    .enabled(Boolean.TRUE.equals(doc.getBoolean("enabled", true)))
                    .sortOrder(intOr(doc, "sortOrder", 0))
                    .system(Boolean.TRUE.equals(doc.getBoolean("system")))
                    .updatedAt(localDateTime(doc.get("updatedAt")))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, tierRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_MEMBERSHIP_TIERS,
                ct -> {
                    String[] parts = ct.split("/", 2);
                    return parts.length == 2 && tierRepo.existsByCategoryCodeAndTierCode(parts[0], parts[1]);
                },
                "categoryCode/tierCode");
    }

    private void copyMembershipTypes(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_MEMBERSHIP_TYPES, mode, summary);
        List<MembershipTypeConfig> batch = new ArrayList<>();
        forEachDoc(C_MEMBERSHIP_TYPES, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            if (code == null || code.isBlank()) { stats.validationError("missing code"); return null; }
            if (typeRepo.findByCode(code).isPresent()) { stats.skippedExisting(); return null; }

            return MembershipTypeConfig.builder()
                    .id(idMap.resolveOrAssign(C_MEMBERSHIP_TYPES, mongoId))
                    .code(code)
                    .displayName(doc.getString("displayName"))
                    .description(doc.getString("description"))
                    .system(Boolean.TRUE.equals(doc.getBoolean("system")))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, typeRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_MEMBERSHIP_TYPES,
                code -> typeRepo.findByCode(code).isPresent(), "code");
    }

    private void copyAiProviders(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_AI_PROVIDERS, mode, summary);
        List<AiModelProvider> batch = new ArrayList<>();
        forEachDoc(C_AI_PROVIDERS, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            if (code == null || code.isBlank()) { stats.validationError("missing code"); return null; }
            if (providerRepo.existsByCode(code)) { stats.skippedExisting(); return null; }

            return AiModelProvider.builder()
                    .id(idMap.resolveOrAssign(C_AI_PROVIDERS, mongoId))
                    .code(code)
                    .displayName(doc.getString("displayName"))
                    .baseUrl(doc.getString("baseUrl"))
                    .enabled(Boolean.TRUE.equals(doc.getBoolean("enabled", true)))
                    .createdAt(instant(doc.get("createdAt"), Instant.now()))
                    .updatedAt(instant(doc.get("updatedAt"), Instant.now()))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, providerRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_AI_PROVIDERS,
                code -> providerRepo.existsByCode(code), "code");
    }

    private void copyAiModels(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_AI_MODELS, mode, summary);
        List<AiModel> batch = new ArrayList<>();
        forEachDoc(C_AI_MODELS, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            String providerCode = doc.getString("providerCode");
            if (code == null || providerCode == null) {
                stats.validationError("missing code/providerCode"); return null;
            }
            if (modelRepo.existsByCode(code)) { stats.skippedExisting(); return null; }

            AiModel.Status status = parseEnum(doc.getString("status"),
                    AiModel.Status.class, AiModel.Status.ENABLED, stats);
            AiModel.LockStyle lockStyle = parseEnum(doc.getString("lockStyle"),
                    AiModel.LockStyle.class, AiModel.LockStyle.LOCK, stats);
            return AiModel.builder()
                    .id(idMap.resolveOrAssign(C_AI_MODELS, mongoId))
                    .code(code)
                    .providerCode(providerCode)
                    .displayName(doc.getString("displayName"))
                    .description(doc.getString("description"))
                    .qualityLevel(intOr(doc, "qualityLevel", 2))
                    .costLevel(intOr(doc, "costLevel", 2))
                    .status(status)
                    .lockStyle(lockStyle)
                    .deprecatedAt(instant(doc.get("deprecatedAt"), null))
                    .replacesModelCode(doc.getString("replacesModelCode"))
                    .sortOrder(intOr(doc, "sortOrder", 100))
                    .createdAt(instant(doc.get("createdAt"), Instant.now()))
                    .updatedAt(instant(doc.get("updatedAt"), Instant.now()))
                    .createdBy(doc.getString("createdBy"))
                    .updatedBy(doc.getString("updatedBy"))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, modelRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_AI_MODELS,
                code -> modelRepo.existsByCode(code), "code");
    }

    private void copyAiTierBindings(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_AI_TIER_BINDINGS, mode, summary);
        List<AiModelTierBinding> batch = new ArrayList<>();
        forEachDoc(C_AI_TIER_BINDINGS, stats, doc -> {
            String mongoId = readId(doc);
            String model = doc.getString("modelCode");
            String category = doc.getString("categoryCode");
            String tier = doc.getString("tierCode");
            if (model == null || category == null || tier == null) {
                stats.validationError("missing modelCode/categoryCode/tierCode"); return null;
            }
            if (bindingRepo.findByModelCodeAndCategoryCodeAndTierCode(model, category, tier).isPresent()) {
                stats.skippedExisting();
                return null;
            }

            return AiModelTierBinding.builder()
                    .id(idMap.resolveOrAssign(C_AI_TIER_BINDINGS, mongoId))
                    .modelCode(model)
                    .categoryCode(category)
                    .tierCode(tier)
                    .isDefault(Boolean.TRUE.equals(doc.getBoolean("isDefault")))
                    .labelOverride(doc.getString("labelOverride"))
                    .createdAt(instant(doc.get("createdAt"), Instant.now()))
                    .updatedAt(instant(doc.get("updatedAt"), Instant.now()))
                    .createdBy(doc.getString("createdBy"))
                    .updatedBy(doc.getString("updatedBy"))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, bindingRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_AI_TIER_BINDINGS,
                key -> {
                    String[] p = key.split("/", 3);
                    return p.length == 3 && bindingRepo
                            .findByModelCodeAndCategoryCodeAndTierCode(p[0], p[1], p[2]).isPresent();
                },
                "modelCode/categoryCode/tierCode");
    }

    private void copyTierEntitlements(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_TIER_ENTITLEMENTS, mode, summary);
        List<TierEntitlement> batch = new ArrayList<>();
        forEachDoc(C_TIER_ENTITLEMENTS, stats, doc -> {
            String mongoId = readId(doc);
            String category = doc.getString("categoryCode");
            String tier = doc.getString("tierCode");
            String key = doc.getString("entitlementKey");
            if (category == null || tier == null || key == null) {
                stats.validationError("missing categoryCode/tierCode/entitlementKey"); return null;
            }
            if (tierEntitlementRepo
                    .findByCategoryCodeAndTierCodeAndEntitlementKey(category, tier, key).isPresent()) {
                stats.skippedExisting();
                return null;
            }

            return TierEntitlement.builder()
                    .id(idMap.resolveOrAssign(C_TIER_ENTITLEMENTS, mongoId))
                    .categoryCode(category)
                    .tierCode(tier)
                    .entitlementKey(key)
                    .value(doc.getString("value"))
                    .updatedAt(localDateTime(doc.get("updatedAt")))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, tierEntitlementRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_TIER_ENTITLEMENTS,
                key -> {
                    String[] p = key.split("/", 3);
                    return p.length == 3 && tierEntitlementRepo
                            .findByCategoryCodeAndTierCodeAndEntitlementKey(p[0], p[1], p[2]).isPresent();
                },
                "categoryCode/tierCode/entitlementKey");
    }

    private void copyLocaleOptions(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_LOCALE_OPTIONS, mode, summary);
        List<LocaleOption> batch = new ArrayList<>();
        forEachDoc(C_LOCALE_OPTIONS, stats, doc -> {
            String mongoId = readId(doc);
            String type = doc.getString("type");
            String code = doc.getString("code");
            if (type == null || code == null) { stats.validationError("missing type/code"); return null; }
            if (localeRepo.existsByTypeAndCode(type, code)) { stats.skippedExisting(); return null; }

            return LocaleOption.builder()
                    .id(idMap.resolveOrAssign(C_LOCALE_OPTIONS, mongoId))
                    .type(type)
                    .code(code)
                    .label(doc.getString("label"))
                    .sortOrder(intOr(doc, "sortOrder", 0))
                    .enabled(Boolean.TRUE.equals(doc.getBoolean("enabled", true)))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, localeRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_LOCALE_OPTIONS,
                key -> {
                    String[] p = key.split("/", 2);
                    return p.length == 2 && localeRepo.existsByTypeAndCode(p[0], p[1]);
                },
                "type/code");
    }

    private void copyAppSettings(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_APP_SETTINGS, mode, summary);
        List<AppSetting> batch = new ArrayList<>();
        forEachDoc(C_APP_SETTINGS, stats, doc -> {
            String mongoId = readId(doc);
            String key = doc.getString("key");
            if (key == null || key.isBlank()) { stats.validationError("missing key"); return null; }
            if (settingRepo.existsByKey(key)) { stats.skippedExisting(); return null; }

            return AppSetting.builder()
                    .id(idMap.resolveOrAssign(C_APP_SETTINGS, mongoId))
                    .key(key)
                    .value(doc.getString("value"))
                    .description(doc.getString("description"))
                    .updatedBy(doc.getString("updatedBy"))
                    .updatedAt(instant(doc.get("updatedAt"), Instant.now()))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, settingRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_APP_SETTINGS,
                key -> settingRepo.existsByKey(key), "key");
    }

    private void copyEnforcementRules(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_ENFORCEMENT_RULES, mode, summary);
        List<EnforcementRule> batch = new ArrayList<>();
        forEachDoc(C_ENFORCEMENT_RULES, stats, doc -> {
            String mongoId = readId(doc);
            String text = doc.getString("text");
            String tier = doc.getString("tier");
            if (text == null || text.isBlank()) { stats.validationError("missing text"); return null; }
            if (tier == null || (!"SYSTEM".equals(tier) && !"OPTIONAL".equals(tier))) {
                stats.validationError("invalid tier: " + tier);
                return null;
            }
            // existsById doesn't cross the Mongo→Postgres ID boundary, so it can't
            // detect duplicates on re-runs. Fall back to a triple-key match on
            // (text, tier, category) which is effectively unique in practice.
            String category = doc.getString("category");
            boolean alreadyPresent = enforcementRepo.findAll().stream()
                    .anyMatch(existing -> java.util.Objects.equals(existing.getText(), text)
                            && java.util.Objects.equals(existing.getTier(), tier)
                            && java.util.Objects.equals(existing.getCategory(), category));
            if (alreadyPresent) { stats.skippedExisting(); return null; }

            UUID newId = idMap.resolveOrAssign(C_ENFORCEMENT_RULES, mongoId);
            return EnforcementRule.builder()
                    .id(newId)
                    .text(text)
                    .tier(tier)
                    .category(category)
                    .enabledByDefault(Boolean.TRUE.equals(doc.getBoolean("enabledByDefault", true)))
                    .sortOrder(intOr(doc, "sortOrder", 0))
                    .active(Boolean.TRUE.equals(doc.getBoolean("active", true)))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, enforcementRepo::saveAllAndFlush));
        // No business-key verify sample — rely on id existence checks.
        finishCollection(mode, summary, stats, C_ENFORCEMENT_RULES, null, "id");
    }

    private void copySignupInviteCodes(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_SIGNUP_INVITE_CODES, mode, summary);
        List<SignupInviteCode> batch = new ArrayList<>();
        forEachDoc(C_SIGNUP_INVITE_CODES, stats, doc -> {
            String mongoId = readId(doc);
            String code = doc.getString("code");
            if (code == null || code.isBlank()) { stats.validationError("missing code"); return null; }
            if (inviteRepo.existsByCode(code)) { stats.skippedExisting(); return null; }

            return SignupInviteCode.builder()
                    .id(idMap.resolveOrAssign(C_SIGNUP_INVITE_CODES, mongoId))
                    .code(code)
                    .description(doc.getString("description"))
                    .maxUses(doc.getInteger("maxUses"))
                    .usedCount(intOr(doc, "usedCount", 0))
                    .expiresAt(instant(doc.get("expiresAt"), null))
                    .active(Boolean.TRUE.equals(doc.getBoolean("active", true)))
                    .createdBy(doc.getString("createdBy"))
                    .createdAt(instant(doc.get("createdAt"), Instant.now()))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, inviteRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_SIGNUP_INVITE_CODES,
                code -> inviteRepo.existsByCode(code), "code");
    }

    private void copyUsers(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_USERS, mode, summary);
        List<User> batch = new ArrayList<>();
        forEachDoc(C_USERS, stats, doc -> {
            String mongoId = readId(doc);
            String username = doc.getString("username");
            String email = doc.getString("email");
            if (username == null || email == null) {
                stats.validationError("missing username/email"); return null;
            }
            if (userRepo.existsByUsername(username) || userRepo.existsByEmail(email)) {
                stats.skippedExisting();
                // Re-point IdMap at the already-migrated row's UUID so downstream
                // FK translation (members.user_id, verification_tokens.user_id,
                // user_preferences / user_avatars PKs, user_roles links) resolves
                // to the real row rather than minting an orphan.
                userRepo.findByUsernameOrEmail(username, email)
                        .ifPresent(u -> idMap.put(C_USERS, mongoId, u.getId()));
                return null;
            }

            AuthProvider provider = parseEnum(doc.getString("provider"),
                    AuthProvider.class, AuthProvider.LOCAL, stats);

            User u = new User();
            u.setId(idMap.resolveOrAssign(C_USERS, mongoId));
            u.setUsername(username);
            u.setEmail(email);
            u.setPassword(doc.getString("password"));
            u.setFirstName(Optional.ofNullable(doc.getString("firstName")).orElse(""));
            u.setLastName(Optional.ofNullable(doc.getString("lastName")).orElse(""));
            u.setPhoneNumber(doc.getString("phoneNumber"));
            u.setActive(Boolean.TRUE.equals(doc.getBoolean("active", true)));
            u.setProvider(provider);
            u.setProviderId(doc.getString("providerId"));
            u.setEmailVerified(Boolean.TRUE.equals(doc.getBoolean("emailVerified")));
            u.setTotpSecret(doc.getString("totpSecret"));
            u.setTotpEnabled(Boolean.TRUE.equals(doc.getBoolean("totpEnabled")));
            @SuppressWarnings("unchecked")
            List<String> recovery = (List<String>) doc.get("totpRecoveryCodes");
            u.setTotpRecoveryCodes(recovery);
            u.setCreatedAt(localDateTime(doc.get("createdAt")));
            u.setUpdatedAt(localDateTime(doc.get("updatedAt")));
            // Roles handled in copyUserRoles — leave empty here.
            u.setRoles(new HashSet<>());
            return u;
        }, batch, () -> flushBatch(mode, stats, batch, userRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_USERS,
                username -> userRepo.existsByUsername(username), "username");
    }

    /**
     * Second pass over the users collection that wires the {@code User.roles}
     * @DBRef set (Mongo) into the {@code user_roles} join table (Postgres).
     *
     * <p>Mongo stored each role reference as a DBRef-style sub-document with a
     * {@code $ref="roles"} / {@code $id=ObjectId} pair. Spring Data Mongo will
     * deserialize that as either a {@link Document} with an {@code _id} field,
     * or as the raw {@link com.mongodb.DBRef} class depending on driver
     * version. We handle both shapes by extracting the referenced mongo id and
     * translating it through the {@link IdMap} (which was populated by
     * {@link #copyRoles}).
     */
    private void copyUserRoles(Mode mode, RunSummary summary) {
        // Only meaningful in MIGRATE mode — dry-run and verify just count.
        CollectionStats stats = beginCollection("user_roles", mode, summary);
        stats.sourceTotal = (int) mongo.count(new Query(), C_USERS); // one per user-role link counted below

        if (mode == Mode.DRY_RUN || mode == Mode.VERIFY) {
            // Count links rather than attempting to copy.
            int linkCount = 0;
            for (Document doc : mongo.findAll(Document.class, C_USERS)) {
                Object roles = doc.get("roles");
                if (roles instanceof List<?> list) linkCount += list.size();
            }
            stats.sourceTotal = linkCount;
            log.info("[{}] user_roles: {} source link(s)", mode.tag(), linkCount);
            finishCollection(mode, summary, stats, "user_roles", null, "link");
            return;
        }

        int saved = 0;
        int failed = 0;
        for (Document doc : mongo.findAll(Document.class, C_USERS)) {
            String mongoUserId = readId(doc);
            UUID userUuid = idMap.get(C_USERS, mongoUserId);
            if (userUuid == null) continue; // user was skipped as existing
            Optional<User> userOpt = userRepo.findById(userUuid);
            if (userOpt.isEmpty()) continue;
            User u = userOpt.get();

            Set<Role> attached = new HashSet<>(u.getRoles());
            int beforeSize = attached.size();
            Object rolesField = doc.get("roles");
            if (rolesField instanceof List<?> list) {
                for (Object ref : list) {
                    String roleMongoId = extractDbRefId(ref);
                    if (roleMongoId == null) continue;
                    UUID roleUuid = idMap.get(C_ROLES, roleMongoId);
                    if (roleUuid == null) continue; // role skipped with no mapping
                    roleRepo.findById(roleUuid).ifPresent(attached::add);
                }
            }
            if (attached.size() == beforeSize) continue;

            try {
                u.setRoles(attached);
                userRepo.save(u);
                saved += (attached.size() - beforeSize);
            } catch (Exception ex) {
                failed++;
                log.warn("user_roles: failed to attach roles for user {} — {}", u.getId(), ex.getMessage());
            }
        }
        stats.written = saved;
        stats.failed = failed;
        log.info("[migrate] user_roles: {} link(s) written, {} failed", saved, failed);
        finishCollection(mode, summary, stats, "user_roles", null, "link");
    }

    private void copyRoleFeatures(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_ROLE_FEATURES, mode, summary);
        List<RoleFeatureMap> batch = new ArrayList<>();
        forEachDoc(C_ROLE_FEATURES, stats, doc -> {
            String mongoId = readId(doc);
            // Legacy mongo field was {@code role} (the role name as a string).
            String roleName = doc.getString("role");
            if (roleName == null || roleName.isBlank()) {
                stats.validationError("missing role");
                return null;
            }
            if (roleFeatureMapRepo.findByRole(roleName).isPresent()) {
                stats.skippedExisting();
                return null;
            }
            @SuppressWarnings("unchecked")
            List<String> codes = (List<String>) doc.get("featureCodes");
            Set<String> featureCodes = codes == null ? new HashSet<>() : new HashSet<>(codes);

            return RoleFeatureMap.builder()
                    .id(idMap.resolveOrAssign(C_ROLE_FEATURES, mongoId))
                    .role(roleName)
                    .featureCodes(featureCodes)
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, roleFeatureMapRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_ROLE_FEATURES,
                role -> roleFeatureMapRepo.findByRole(role).isPresent(), "role");
    }

    private void copyMembers(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_MEMBERS, mode, summary);
        List<Member> batch = new ArrayList<>();
        forEachDoc(C_MEMBERS, stats, doc -> {
            String mongoId = readId(doc);
            String membershipId = doc.getString("membershipId");
            if (membershipId == null || membershipId.isBlank()) {
                stats.validationError("missing membershipId");
                return null;
            }
            if (memberRepo.existsByMembershipId(membershipId)) {
                stats.skippedExisting();
                return null;
            }

            // FK resolution — Mongo stored a userId string (either hex ObjectId
            // or the denormalized user_snapshot.id). Translate through IdMap.
            String mongoUserId = doc.getString("userId");
            if (mongoUserId == null) {
                // Fallback: read embedded user.id (UserSummary).
                Document userDoc = doc.get("user", Document.class);
                if (userDoc != null) mongoUserId = userDoc.getString("id");
            }
            UUID userUuid = mongoUserId == null ? null : idMap.get(C_USERS, mongoUserId);
            if (userUuid == null) {
                stats.validationError("member[" + membershipId + "] unresolved userId: " + mongoUserId);
                return null;
            }

            MembershipType membershipType = parseEnum(doc.getString("membershipType"),
                    MembershipType.class, MembershipType.BASIC, stats);
            MembershipStatus status = parseEnum(doc.getString("status"),
                    MembershipStatus.class, MembershipStatus.ACTIVE, stats);

            Member m = new Member();
            m.setId(idMap.resolveOrAssign(C_MEMBERS, mongoId));
            m.setMembershipId(membershipId);
            m.setUserId(userUuid);

            Document userDoc = doc.get("user", Document.class);
            UserSummary summaryEmb = new UserSummary();
            summaryEmb.setId(userUuid.toString());
            if (userDoc != null) {
                summaryEmb.setUsername(userDoc.getString("username"));
                summaryEmb.setEmail(userDoc.getString("email"));
                summaryEmb.setFirstName(userDoc.getString("firstName"));
                summaryEmb.setLastName(userDoc.getString("lastName"));
                summaryEmb.setPhoneNumber(userDoc.getString("phoneNumber"));
                summaryEmb.setActive(userDoc.getBoolean("active"));
            }
            m.setUser(summaryEmb);
            m.setMembershipType(membershipType);
            m.setCategoryCode(doc.getString("categoryCode"));
            m.setTierCode(doc.getString("tierCode"));
            m.setStatus(status);
            m.setMembershipStartDate(localDate(doc.get("membershipStartDate")));
            m.setMembershipEndDate(localDate(doc.get("membershipEndDate")));
            m.setNotes(doc.getString("notes"));
            m.setIsActive(Boolean.TRUE.equals(doc.getBoolean("isActive", true)));
            m.setCreatedAt(localDateTime(doc.get("createdAt")));
            m.setUpdatedAt(localDateTime(doc.get("updatedAt")));
            return m;
        }, batch, () -> flushBatch(mode, stats, batch, memberRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_MEMBERS,
                id -> memberRepo.existsByMembershipId(id), "membershipId");
    }

    private void copyUserPreferences(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_USER_PREFERENCES, mode, summary);
        List<UserPreferences> batch = new ArrayList<>();
        forEachDoc(C_USER_PREFERENCES, stats, doc -> {
            String mongoUserId = Optional.ofNullable(doc.getString("userId")).orElse(readId(doc));
            UUID userUuid = idMap.get(C_USERS, mongoUserId);
            if (userUuid == null) {
                stats.validationError("prefs: unresolved userId " + mongoUserId);
                return null;
            }
            if (prefsRepo.existsById(userUuid)) {
                stats.skippedExisting();
                return null;
            }
            UserPreferences p = new UserPreferences();
            p.setUserId(userUuid);
            p.setTheme(Optional.ofNullable(doc.getString("theme")).orElse("system"));
            p.setLanguage(Optional.ofNullable(doc.getString("language")).orElse("en"));
            p.setCountry(doc.getString("country"));
            p.setTimezone(doc.getString("timezone"));
            p.setEmailNotifications(Boolean.TRUE.equals(doc.getBoolean("emailNotifications", true)));
            p.setNavbarDisplay(Optional.ofNullable(doc.getString("navbarDisplay")).orElse("avatar"));
            return p;
        }, batch, () -> flushBatch(mode, stats, batch, prefsRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_USER_PREFERENCES, null, "userId");
    }

    private void copyUserAvatars(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_USER_AVATARS, mode, summary);
        List<UserAvatar> batch = new ArrayList<>();
        forEachDoc(C_USER_AVATARS, stats, doc -> {
            String mongoUserId = Optional.ofNullable(doc.getString("userId")).orElse(readId(doc));
            UUID userUuid = idMap.get(C_USERS, mongoUserId);
            if (userUuid == null) {
                stats.validationError("avatar: unresolved userId " + mongoUserId);
                return null;
            }
            if (avatarRepo.existsById(userUuid)) {
                stats.skippedExisting();
                return null;
            }
            String contentType = doc.getString("contentType");
            byte[] bytes = extractBytes(doc.get("data"));
            if (contentType == null || bytes == null) {
                stats.validationError("avatar: missing contentType/data");
                return null;
            }
            UserAvatar a = new UserAvatar();
            a.setUserId(userUuid);
            a.setContentType(contentType);
            a.setData(bytes);
            return a;
        }, batch, () -> flushBatch(mode, stats, batch, avatarRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_USER_AVATARS, null, "userId");
    }

    private void copyVerificationTokens(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_VERIFICATION_TOKENS, mode, summary);
        List<VerificationToken> batch = new ArrayList<>();
        forEachDoc(C_VERIFICATION_TOKENS, stats, doc -> {
            String mongoId = readId(doc);
            String token = doc.getString("token");
            String mongoUserId = doc.getString("userId");
            if (token == null || mongoUserId == null) {
                stats.validationError("missing token/userId");
                return null;
            }
            if (tokenRepo.findByToken(token).isPresent()) {
                stats.skippedExisting();
                return null;
            }
            UUID userUuid = idMap.get(C_USERS, mongoUserId);
            if (userUuid == null) {
                stats.validationError("token: unresolved userId " + mongoUserId);
                return null;
            }
            VerificationToken.TokenType type = parseEnum(doc.getString("type"),
                    VerificationToken.TokenType.class, VerificationToken.TokenType.EMAIL_VERIFICATION, stats);

            return VerificationToken.builder()
                    .id(idMap.resolveOrAssign(C_VERIFICATION_TOKENS, mongoId))
                    .token(token)
                    .userId(userUuid)
                    .type(type)
                    .createdAt(instant(doc.get("createdAt"), Instant.now()))
                    .expiresAt(instant(doc.get("expiresAt"), Instant.now().plusSeconds(3600)))
                    .consumedAt(instant(doc.get("consumedAt"), null))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, tokenRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_VERIFICATION_TOKENS,
                t -> tokenRepo.findByToken(t).isPresent(), "token");
    }

    private void copyAiModelAudit(Mode mode, RunSummary summary) {
        CollectionStats stats = beginCollection(C_AI_MODEL_AUDIT, mode, summary);
        List<AiModelAudit> batch = new ArrayList<>();
        forEachDoc(C_AI_MODEL_AUDIT, stats, doc -> {
            String mongoId = readId(doc);
            String actionStr = doc.getString("action");
            if (actionStr == null) { stats.validationError("missing action"); return null; }

            AiModelAudit.Action action = parseEnum(actionStr,
                    AiModelAudit.Action.class, null, stats);
            if (action == null) return null;

            UUID newId = idMap.resolveOrAssign(C_AI_MODEL_AUDIT, mongoId);
            if (auditRepo.existsById(newId)) { stats.skippedExisting(); return null; }

            // actorId in Mongo could be a String or a DBRef (legacy). Keep it
            // as the raw string — the Postgres column is VARCHAR(128).
            String actorId = doc.getString("actorId");

            Document bindingDoc = doc.get("binding", Document.class);
            AiModelAudit.BindingRef ref = null;
            if (bindingDoc != null) {
                ref = new AiModelAudit.BindingRef(
                        bindingDoc.getString("categoryCode"),
                        bindingDoc.getString("tierCode"));
            }

            return AiModelAudit.builder()
                    .id(newId)
                    .actorId(actorId)
                    .action(action)
                    .modelCode(doc.getString("modelCode"))
                    .binding(ref)
                    .beforeJson(readJsonString(doc.get("beforeJson")))
                    .afterJson(readJsonString(doc.get("afterJson")))
                    .ip(doc.getString("ip"))
                    .userAgent(doc.getString("userAgent"))
                    .at(instant(doc.get("at"), Instant.now()))
                    .build();
        }, batch, () -> flushBatch(mode, stats, batch, auditRepo::saveAllAndFlush));
        finishCollection(mode, summary, stats, C_AI_MODEL_AUDIT, null, "id");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Batch / flush helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Iterate every doc in {@code collection} (paged with {@link MigrationProperties#getBatchSize()})
     * and call {@code mapper}. A non-null mapper return means "queue me for
     * the write path"; a null return means "skip silently, already counted
     * elsewhere (validation error / already-existing)".
     */
    private <T> void forEachDoc(String collection,
                                CollectionStats stats,
                                Function<Document, T> mapper,
                                List<T> batch,
                                Runnable flushFn) {
        long total = mongo.count(new Query(), collection);
        stats.sourceTotal = (int) total;
        int page = 0;
        int pageSize = Math.max(1, properties.getBatchSize());
        long seen = 0;
        while (seen < total) {
            Query q = new Query().skip(page * (long) pageSize).limit(pageSize);
            // Stable ordering by _id so re-runs visit documents the same way.
            q.with(Sort.by(Sort.Direction.ASC, "_id"));
            List<Document> docs = mongo.find(q, Document.class, collection);
            if (docs.isEmpty()) break;
            for (Document d : docs) {
                try {
                    T entity = mapper.apply(d);
                    if (entity != null) batch.add(entity);
                } catch (Exception ex) {
                    stats.failed++;
                    log.warn("[{}] {}: mapper failure on _id={} — {}",
                            stats.mode.tag(), collection, readId(d), ex.getMessage());
                }
                if (batch.size() >= pageSize) flushFn.run();
            }
            seen += docs.size();
            page++;
        }
        flushFn.run();
    }

    /**
     * In migrate mode, batch-flush via {@link EntityManager#persist(Object)}
     * inside a single transaction. Per-chunk failure does NOT abort the run —
     * each chunk is tried, and on failure we fall back to single-row saves so
     * good rows still land.
     *
     * <p>The {@code saveAllAndFlush} parameter is retained for call-site
     * compatibility but unused; we go through the EntityManager directly so
     * we can call {@code persist()} rather than {@code save()}. See the
     * {@code em} field comment for why.
     */
    private <T> void flushBatch(Mode mode,
                                CollectionStats stats,
                                List<T> batch,
                                Function<List<T>, List<T>> saveAllAndFlush) {
        if (batch.isEmpty()) return;
        if (mode != Mode.MIGRATE) {
            // Dry-run and verify paths never write.
            stats.written += batch.size();
            batch.clear();
            return;
        }
        List<T> snapshot = new ArrayList<>(batch);
        try {
            persistAll(snapshot);
            stats.written += snapshot.size();
        } catch (Exception chunkEx) {
            log.warn("Chunk save failed ({} rows) — retrying one-by-one. cause: {}",
                    snapshot.size(), chunkEx.getMessage());
            // Per-row retry so one bad row doesn't take out the whole chunk.
            for (T row : snapshot) {
                try {
                    persistAll(List.of(row));
                    stats.written++;
                } catch (Exception rowEx) {
                    stats.failed++;
                    log.warn("row save failed — {}", rowEx.getMessage());
                }
            }
        } finally {
            batch.clear();
        }
    }

    /** Bulk-insert via Hibernate StatelessSession. Each call gets a fresh
     *  session + transaction, so a failed batch rolls back without poisoning
     *  subsequent retries. */
    private <T> void persistAll(List<T> rows) {
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        try (StatelessSession s = sf.openStatelessSession()) {
            org.hibernate.Transaction txn = s.beginTransaction();
            try {
                for (T row : rows) s.insert(row);
                txn.commit();
            } catch (RuntimeException ex) {
                txn.rollback();
                throw ex;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Reporting helpers
    // ══════════════════════════════════════════════════════════════════════════

    private CollectionStats beginCollection(String collection, Mode mode, RunSummary summary) {
        CollectionStats stats = new CollectionStats(collection, mode);
        summary.perCollection.put(collection, stats);
        return stats;
    }

    private void finishCollection(Mode mode,
                                  RunSummary summary,
                                  CollectionStats stats,
                                  String collection,
                                  BusinessKeyCheck existsCheck,
                                  String businessKeyLabel) {
        String line = String.format(
                "[%s] %-24s source=%d written=%d skipped-existing=%d validation-errors=%d failed=%d",
                mode.tag(), collection,
                stats.sourceTotal, stats.written, stats.skippedExisting,
                stats.validationErrors, stats.failed);
        log.info(line);

        if (mode == Mode.VERIFY) {
            long target = countTarget(collection);
            log.info("[verify] {} postgres row count = {}", collection, target);
            if (stats.sourceTotal != target) {
                log.warn("[verify] {} count delta: mongo={} postgres={}", collection, stats.sourceTotal, target);
                summary.countDeltas++;
            }
            verifySample(collection, existsCheck, businessKeyLabel);
        }
    }

    /** Randomly sample up to 5 docs and confirm their business key resolves in Postgres. */
    private void verifySample(String collection, BusinessKeyCheck existsCheck, String businessKeyLabel) {
        if (existsCheck == null) return;
        long count = mongo.count(new Query(), collection);
        int sampleSize = (int) Math.min(5, count);
        if (sampleSize == 0) return;
        List<Document> all = mongo.findAll(Document.class, collection);
        Random r = new Random(42);
        Collections.shuffle(all, r);
        int mismatched = 0;
        for (int i = 0; i < sampleSize && i < all.size(); i++) {
            Document d = all.get(i);
            String key = sampleKeyFor(collection, d);
            if (key == null) continue;
            if (!existsCheck.exists(key)) {
                log.warn("[verify] {}: {}={} present in mongo but missing in postgres",
                        collection, businessKeyLabel, key);
                mismatched++;
            }
        }
        if (mismatched > 0) {
            log.warn("[verify] {}: {}/{} sample mismatches", collection, mismatched, sampleSize);
        }
    }

    /** Extract a business-key string for verify-mode sampling. */
    private String sampleKeyFor(String collection, Document d) {
        return switch (collection) {
            case C_USERS -> d.getString("username");
            case C_ROLES -> d.getString("name");
            case C_FEATURES, C_SIGNUP_INVITE_CODES, C_APP_SETTINGS,
                 C_MEMBERSHIP_CATEGORIES, C_MEMBERSHIP_TYPES,
                 C_AI_PROVIDERS, C_AI_MODELS -> d.getString("code");
            case C_ENTITLEMENTS -> d.getString("key");
            case C_MEMBERS -> d.getString("membershipId");
            case C_VERIFICATION_TOKENS -> d.getString("token");
            case C_LOCALE_OPTIONS ->
                    (d.getString("type") == null ? "" : d.getString("type"))
                    + "/" + d.getString("code");
            case C_MEMBERSHIP_TIERS ->
                    d.getString("categoryCode") + "/" + d.getString("tierCode");
            case C_TIER_ENTITLEMENTS ->
                    d.getString("categoryCode") + "/" + d.getString("tierCode")
                    + "/" + d.getString("entitlementKey");
            case C_AI_TIER_BINDINGS ->
                    d.getString("modelCode") + "/" + d.getString("categoryCode")
                    + "/" + d.getString("tierCode");
            case C_ROLE_FEATURES -> d.getString("role");
            default -> null;
        };
    }

    private long countTarget(String collection) {
        return switch (collection) {
            case C_ROLES -> roleRepo.count();
            case C_FEATURES -> featureRepo.count();
            case C_ENTITLEMENTS -> entitlementRepo.count();
            case C_MEMBERSHIP_CATEGORIES -> categoryRepo.count();
            case C_MEMBERSHIP_TIERS -> tierRepo.count();
            case C_MEMBERSHIP_TYPES -> typeRepo.count();
            case C_AI_PROVIDERS -> providerRepo.count();
            case C_AI_MODELS -> modelRepo.count();
            case C_AI_TIER_BINDINGS -> bindingRepo.count();
            case C_TIER_ENTITLEMENTS -> tierEntitlementRepo.count();
            case C_LOCALE_OPTIONS -> localeRepo.count();
            case C_APP_SETTINGS -> settingRepo.count();
            case C_ENFORCEMENT_RULES -> enforcementRepo.count();
            case C_SIGNUP_INVITE_CODES -> inviteRepo.count();
            case C_USERS -> userRepo.count();
            case C_ROLE_FEATURES -> roleFeatureMapRepo.count();
            case C_MEMBERS -> memberRepo.count();
            case C_USER_PREFERENCES -> prefsRepo.count();
            case C_USER_AVATARS -> avatarRepo.count();
            case C_VERIFICATION_TOKENS -> tokenRepo.count();
            case C_AI_MODEL_AUDIT -> auditRepo.count();
            default -> -1;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Conversion helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Pull _id as a string, handling both ObjectId and plain-string _ids. */
    private static String readId(Document doc) {
        Object raw = doc.get("_id");
        return raw == null ? null : raw.toString();
    }

    private static <E extends Enum<E>> E parseEnum(String raw,
                                                    Class<E> type,
                                                    E fallback,
                                                    CollectionStats stats) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ex) {
            if (stats != null) stats.validationError("unknown enum " + type.getSimpleName() + ": " + raw);
            return fallback;
        }
    }

    private static int intOr(Document doc, String field, int fallback) {
        Object v = doc.get(field);
        if (v instanceof Number n) return n.intValue();
        return fallback;
    }

    private static LocalDateTime localDateTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date d) return LocalDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC);
        if (raw instanceof Instant i) return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
        return null;
    }

    private static Instant instant(Object raw, Instant fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Date d) return d.toInstant();
        if (raw instanceof Instant i) return i;
        return fallback;
    }

    private static LocalDate localDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date d) return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        if (raw instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception ignore) { return null; }
        }
        return null;
    }

    /** Pull the referenced {@code _id} from a Mongo DBRef-like sub-document or DBRef instance. */
    private static String extractDbRefId(Object ref) {
        if (ref == null) return null;
        if (ref instanceof Document d) {
            Object id = d.get("_id");
            if (id == null) id = d.get("$id");
            if (id == null) id = d.get("id");
            return id == null ? null : stringifyId(id);
        }
        if (ref instanceof Map<?,?> m) {
            Object id = m.get("_id");
            if (id == null) id = m.get("$id");
            if (id == null) id = m.get("id");
            return id == null ? null : stringifyId(id);
        }
        // com.mongodb.DBRef path — read reflectively so we don't need to import.
        try {
            Object id = ref.getClass().getMethod("getId").invoke(ref);
            return id == null ? null : stringifyId(id);
        } catch (ReflectiveOperationException ex) {
            return ref.toString();
        }
    }

    /**
     * Normalise whatever the BSON/Mongo driver handed us back as an id to the
     * same string form {@link #readId(Document)} produces for a top-level
     * {@code _id}. ObjectId.toString() and BsonObjectId.getValue().toString()
     * both give the 24-char hex; BsonObjectId.toString() by itself returns
     * {@code BsonObjectId{value=...}}, which is NOT what readId returns —
     * that mismatch is what prevented cross-table FK resolution.
     */
    private static String stringifyId(Object id) {
        if (id == null) return null;
        if (id instanceof org.bson.types.ObjectId oid) return oid.toHexString();
        if (id instanceof org.bson.BsonObjectId bson) return bson.getValue().toHexString();
        return id.toString();
    }

    private static byte[] extractBytes(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Binary b) return b.getData();
        if (raw instanceof byte[] arr) return arr;
        return null;
    }

    /** Mongo may store JSON-ish audit snapshots as {@link Document} or already as {@link String}. */
    private static String readJsonString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) return s;
        if (raw instanceof Document d) return d.toJson();
        return raw.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Summary types
    // ══════════════════════════════════════════════════════════════════════════

    public enum Mode {
        DRY_RUN("dry-run"), MIGRATE("migrate"), VERIFY("verify");
        private final String tag;
        Mode(String t) { this.tag = t; }
        public String tag() { return tag; }
    }

    @FunctionalInterface
    private interface BusinessKeyCheck {
        boolean exists(String key);
    }

    public static final class CollectionStats {
        final String collection;
        final Mode mode;
        int sourceTotal;
        int written;
        int skippedExisting;
        int validationErrors;
        int failed;

        CollectionStats(String collection, Mode mode) {
            this.collection = collection;
            this.mode = mode;
        }
        void skippedExisting() { skippedExisting++; }
        void validationError(String msg) {
            validationErrors++;
            log.debug("[{}] {}: {}", mode.tag(), collection, msg);
        }
    }

    public static final class RunSummary {
        final Mode mode;
        final Map<String, CollectionStats> perCollection = new LinkedHashMap<>();
        int countDeltas;

        RunSummary(Mode mode) { this.mode = mode; }

        public boolean hasFailures() {
            if (mode == Mode.VERIFY) return countDeltas > 0;
            return perCollection.values().stream().anyMatch(s -> s.failed > 0);
        }

        public int totalFailures() {
            if (mode == Mode.VERIFY) return countDeltas;
            return perCollection.values().stream().mapToInt(s -> s.failed).sum();
        }

        public void logFinalTable(Logger out) {
            out.info("========================= {} summary =========================", mode.tag());
            out.info(String.format("%-24s %10s %10s %10s %10s %10s",
                    "collection", "source", "written", "existing", "val-err", "failed"));
            for (String c : ALL_COLLECTIONS) {
                CollectionStats s = perCollection.get(c);
                if (s == null) continue;
                out.info(String.format("%-24s %10d %10d %10d %10d %10d",
                        s.collection, s.sourceTotal, s.written,
                        s.skippedExisting, s.validationErrors, s.failed));
            }
            if (mode == Mode.VERIFY) {
                out.info("count-deltas: {}", countDeltas);
            }
            out.info("=============================================================");
        }
    }
}
