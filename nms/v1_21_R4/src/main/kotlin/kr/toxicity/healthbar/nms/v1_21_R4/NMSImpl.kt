package kr.toxicity.healthbar.nms.v1_21_R4

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup
import com.mojang.math.Transformation
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.entity.LookAnchor
import kr.toxicity.healthbar.api.BetterHealthBar
import kr.toxicity.healthbar.api.nms.NMS
import kr.toxicity.healthbar.api.nms.PacketBundler
import kr.toxicity.healthbar.api.nms.VirtualTextDisplay
import kr.toxicity.healthbar.api.player.HealthBarPlayer
import kr.toxicity.healthbar.api.trigger.HealthBarTriggerType
import kr.toxicity.healthbar.api.trigger.PacketTrigger
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.pointer.Pointers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.util.Brightness
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.level.entity.LevelEntityGetterAdapter
import net.minecraft.world.level.entity.PersistentEntitySectionManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.WorldBorder
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer
import org.bukkit.craftbukkit.util.CraftChatMessage
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.PlayerInventory
import org.bukkit.permissions.Permission
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

@Suppress("UNUSED")
class NMSImpl : NMS {
    private val plugin
        get() = BetterHealthBar.inst()

    private val getConnection: (ServerCommonPacketListenerImpl) -> Connection = if (plugin.isPaper) {
        {
            it.connection
        }
    } else {
        ServerCommonPacketListenerImpl::class.java.declaredFields.first { f ->
            f.type == Connection::class.java
        }.apply {
            isAccessible = true
        }.let { get ->
            {
                get[it] as Connection
            }
        }
    }
    private val entityTracker = ServerLevel::class.java.fields.firstOrNull {
        it.type == PersistentEntitySectionManager::class.java
    }?.apply { 
        isAccessible = true
    }

    private val getEntityById: (LevelEntityGetter<net.minecraft.world.entity.Entity>, Int) -> net.minecraft.world.entity.Entity? = if (plugin.isPaper) EntityLookup::class.java.declaredFields.first {
        ConcurrentLong2ReferenceChainedHashTable::class.java.isAssignableFrom(it.type)
    }.let {
        it.isAccessible = true
        { e, i ->
            (it[e] as ConcurrentLong2ReferenceChainedHashTable<*>)[i.toLong()] as? net.minecraft.world.entity.Entity
        }
    } else LevelEntityGetterAdapter::class.java.declaredFields.first {
        net.minecraft.world.level.entity.EntityLookup::class.java.isAssignableFrom(it.type)
    }.let {
        it.isAccessible = true
        { e, i ->
            (it[e] as net.minecraft.world.level.entity.EntityLookup<*>).getEntity(i) as? net.minecraft.world.entity.Entity
        }
    }
    private val getEntityFromMovePacket: (ClientboundMoveEntityPacket) -> Int = ClientboundMoveEntityPacket::class.java.declaredFields.first {
        Integer.TYPE.isAssignableFrom(it.type)
    }.let {
        it.isAccessible = true
        { p ->
            it[p] as Int
        }
    }
    private val textVanilla: (Component) -> net.minecraft.network.chat.Component = if (plugin.isPaper) {
        {
            PaperAdventure.asVanilla(it)
        }
    } else {
        {
            CraftChatMessage.fromJSON(GsonComponentSerializer.gson().serialize(it))
        }
    }

    override fun foliaAdapt(player: Player): Player {
        player as CraftPlayer
        fun vanillaPlayer() = (if (plugin.isPaper) player.handleRaw else player.handle) as ServerPlayer
        return object : CraftPlayer(Bukkit.getServer() as CraftServer, vanillaPlayer()) {
            override fun getPersistentDataContainer(): CraftPersistentDataContainer {
                return player.persistentDataContainer
            }
            override fun getHandle(): ServerPlayer {
                return vanillaPlayer()
            }
            override fun getHealth(): Double {
                return player.health
            }
            override fun getScaledHealth(): Float {
                return player.scaledHealth
            }
            override fun getFirstPlayed(): Long {
                return player.firstPlayed
            }
            override fun getInventory(): PlayerInventory {
                return player.inventory
            }
            override fun getEnderChest(): Inventory {
                return player.enderChest
            }

            override fun getCooldown(key: Key): Int {
                return player.getCooldown(key)
            }

            override fun setCooldown(key: Key, i: Int) {
                return player.setCooldown(key, i)
            }

            override fun isOp(): Boolean {
                return player.isOp
            }
            override fun getGameMode(): GameMode {
                return player.gameMode
            }
            override fun getEquipment(): EntityEquipment {
                return player.equipment
            }
            override fun hasPermission(name: String): Boolean {
                return player.hasPermission(name)
            }
            override fun hasPermission(perm: Permission): Boolean {
                return player.hasPermission(perm)
            }
            override fun isPermissionSet(name: String): Boolean {
                return player.isPermissionSet(name)
            }
            override fun isPermissionSet(perm: Permission): Boolean {
                return player.isPermissionSet(perm)
            }
            override fun hasPlayedBefore(): Boolean {
                return player.hasPlayedBefore()
            }
            override fun getWorldBorder(): WorldBorder? {
                return player.worldBorder
            }
            override fun showBossBar(bar: BossBar) {
                player.showBossBar(bar)
            }
            override fun hideBossBar(bar: BossBar) {
                player.hideBossBar(bar)
            }
            override fun sendMessage(message: String) {
                player.sendMessage(message)
            }
            override fun getLastDamageCause(): EntityDamageEvent? {
                return player.lastDamageCause
            }
            override fun pointers(): Pointers {
                return player.pointers()
            }
            override fun spigot(): Player.Spigot {
                return player.spigot()
            }
        }
    }

    override fun foliaAdapt(entity: org.bukkit.entity.LivingEntity): org.bukkit.entity.LivingEntity {
        entity as CraftLivingEntity
        fun vanillaEntity() = (if (plugin.isPaper) entity.handleRaw else entity.handle) as LivingEntity
        return object : CraftLivingEntity(Bukkit.getServer() as CraftServer, vanillaEntity()) {
            override fun getPersistentDataContainer(): CraftPersistentDataContainer {
                return entity.persistentDataContainer
            }
            override fun getHandle(): LivingEntity {
                return vanillaEntity()
            }
            override fun lookAt(p0: Double, p1: Double, p2: Double, p3: LookAnchor) {
                return entity.lookAt(p0, p1, p2, p3)
            }
            override fun getEquipment(): EntityEquipment? {
                return entity.equipment
            }
            override fun hasPermission(name: String): Boolean {
                return entity.hasPermission(name)
            }
            override fun hasPermission(perm: Permission): Boolean {
                return entity.hasPermission(perm)
            }
            override fun isPermissionSet(name: String): Boolean {
                return entity.isPermissionSet(name)
            }
            override fun isPermissionSet(perm: Permission): Boolean {
                return entity.isPermissionSet(perm)
            }
            override fun showBossBar(bar: BossBar) {
                entity.showBossBar(bar)
            }
            override fun hideBossBar(bar: BossBar) {
                entity.hideBossBar(bar)
            }
            override fun sendMessage(message: String) {
                entity.sendMessage(message)
            }
            override fun getLastDamageCause(): EntityDamageEvent? {
                return entity.lastDamageCause
            }
            override fun pointers(): Pointers {
                return entity.pointers()
            }

            override fun spigot(): Entity.Spigot {
                return entity.spigot()
            }
        }
    }

    private fun net.minecraft.world.entity.Entity.moveTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) = snapTo(x, y, z, yaw, pitch)

    override fun createBundler(): PacketBundler = bundlerOf()
    override fun createTextDisplay(location: Location, component: Component): VirtualTextDisplay {
        val display = TextDisplay(EntityType.TEXT_DISPLAY, (location.world as CraftWorld).handle).apply {
            billboardConstraints = Display.BillboardConstraints.CENTER
            entityData.run {
                set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, 3)
                set(TextDisplay.DATA_BACKGROUND_COLOR_ID, 0)
                set(TextDisplay.DATA_LINE_WIDTH_ID, Int.MAX_VALUE)
            }
            brightnessOverride = Brightness(15, 15)
            text = textVanilla(component)
            viewRange = 10F
            moveTo(
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch
            )
        }
        return object : VirtualTextDisplay {
            override fun spawn(bundler: PacketBundler) {
                display.run {
                    bundler += ClientboundAddEntityPacket(
                        id,
                        uuid,
                        x,
                        y,
                        z,
                        xRot,
                        yRot,
                        type,
                        0,
                        deltaMovement,
                        yHeadRot.toDouble()
                    )
                    bundler += ClientboundSetEntityDataPacket(id, entityData.nonDefaultValues!!)
                }
            }
            override fun shadowRadius(radius: Float) {
                display.shadowRadius = radius
            }
            override fun shadowStrength(strength: Float) {
                display.shadowStrength = strength
            }
            override fun update(bundler: PacketBundler) {
                bundler += ClientboundEntityPositionSyncPacket(display.id, PositionMoveRotation.of(display), display.onGround)
                display.entityData.packDirty()?.let {
                    bundler += ClientboundSetEntityDataPacket(display.id, it)
                }
            }
            override fun teleport(location: Location) {
                display.moveTo(
                    location.x,
                    location.y,
                    location.z,
                    location.yaw,
                    location.pitch
                )
            }

            override fun text(component: Component) {
                display.text = textVanilla(component.compact())
            }

            override fun transformation(location: Vector, scale: Vector) {
                fun Vector.toVanilla() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
                display.setTransformation(Transformation(location.toVanilla(), null, scale.toVanilla(), null))
            }

            override fun remove(bundler: PacketBundler) {
                bundler += ClientboundRemoveEntitiesPacket(display.id)
            }
        }
    }

    private val injectionMap = ConcurrentHashMap<UUID, PlayerInjection>()

    override fun inject(player: HealthBarPlayer) {
        injectionMap.computeIfAbsent(player.player().uniqueId) {
            PlayerInjection(player)
        }
    }

    override fun uninject(player: HealthBarPlayer) {
        injectionMap.remove(player.player().uniqueId)?.uninject()
    }

    private inner class PlayerInjection(val player: HealthBarPlayer) : ChannelDuplexHandler() {
        private val serverPlayer = (player.player() as CraftPlayer).handle
        private val world = player.player().world
        private val connection = serverPlayer.connection
        private val foliaAdapted = foliaAdapt(player.player())
        private val taskQueue = ConcurrentLinkedQueue<() -> Unit>()
        private val task = BetterHealthBar.inst().scheduler().asyncTaskTimer(1, 1) {
            var task: (() -> Unit)?
            do {
                task = taskQueue.poll()?.also { 
                    it()
                }
            } while (task != null)
        }

        init {
            val pipeLine = getConnection(connection).channel.pipeline()
            pipeLine.toMap().forEach {
                if (it.value is Connection) pipeLine.addBefore(it.key, BetterHealthBar.NAMESPACE, this)
            }
        }

        fun uninject() {
            task.cancel()
            val channel = getConnection(connection).channel
            channel.eventLoop().submit {
                channel.pipeline().remove(BetterHealthBar.NAMESPACE)
            }
        }
            

        private fun Double.square() = this * this
        private fun show(handle: Any, trigger: HealthBarTriggerType, entity: net.minecraft.world.entity.Entity?) {
            if (entity is LivingEntity && !entity.isDeadOrDying && entity.removalReason == null) taskQueue.add task@ {
                val bukkit = entity.bukkitEntity as org.bukkit.entity.LivingEntity
                if (sqrt((serverPlayer.x - entity.x).square()  + (serverPlayer.y - entity.y).square() + (serverPlayer.z - entity.z).square()) > plugin.configManager().lookDistance()) return@task
                val set = plugin.healthBarManager().allHealthBars().filter {
                    it.triggers().contains(trigger)
                }.toSet()
                val adapt = plugin.mobManager().entity(
                    if (bukkit is Player) injectionMap[bukkit.uniqueId]?.foliaAdapted ?: return@task else foliaAdapt(bukkit)
                )
                val types = adapt.mob()?.configuration()?.types()
                val packet = PacketTrigger(trigger, handle)
                set.filter {
                    (adapt.mob()?.configuration()?.ignoreDefault() != true && it.isDefault) || (types != null && it.applicableTypes().any { t ->
                        types.contains(t)
                    })
                }.forEach {
                    player.showHealthBar(it, packet, adapt)
                }
                adapt.mob()?.configuration()?.healthBars()?.forEach {
                    player.showHealthBar(it, packet, adapt)
                }
            }
        }

        private fun getViewedEntity(): List<LivingEntity> {
            return getLevelGetter().all
                .asSequence()
                .mapNotNull { 
                    it as? LivingEntity
                }
                .filter { 
                    it !== serverPlayer && it.canSee()
                }
                .toList()
        }

        private fun net.minecraft.world.entity.Entity.canSee(): Boolean {
            val playerYaw = Math.toRadians(serverPlayer.yRot.toDouble())
            val playerPitch = Math.toRadians(-serverPlayer.xRot.toDouble())

            val degree = plugin.configManager().lookDegree()

            val x = this.z - serverPlayer.z
            val y = this.y - serverPlayer.y
            val z = -(this.x - serverPlayer.x)

            val dy = abs(atan2(y, abs(cos(playerYaw) * x - sin(playerYaw) * z)) - playerPitch)
            val dz = abs(atan2(z, x) - playerYaw)
            return (dy <= degree || dy >= 2 * PI - degree) && (dz <= degree || dz >= 2 * PI - degree)
        }

        @Suppress("UNCHECKED_CAST")
        private fun getLevelGetter(): LevelEntityGetter<net.minecraft.world.entity.Entity> {
            return if (plugin.isPaper) {
                serverPlayer.serverLevel().`moonrise$getEntityLookup`()
            } else {
                entityTracker?.get(serverPlayer.serverLevel())?.let {
                    (it as PersistentEntitySectionManager<*>).entityGetter as LevelEntityGetter<net.minecraft.world.entity.Entity>
                } ?: throw RuntimeException("LevelEntityGetter")
            }
        }

        private fun Int.toEntity() = getEntityById(getLevelGetter(), this)

        override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
            when (msg) {
                is ClientboundDamageEventPacket -> show(msg, HealthBarTriggerType.DAMAGE, msg.entityId.toEntity())
                is ClientboundMoveEntityPacket -> show(msg, HealthBarTriggerType.MOVE, getEntityFromMovePacket(msg).toEntity())
            }
            super.write(ctx, msg, promise)
        }

        override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
            when (msg) {
                is ServerboundMovePlayerPacket -> taskQueue.add {
                    getViewedEntity().forEach {
                        show(msg, HealthBarTriggerType.LOOK, it)
                    }
                }
            }
            super.channelRead(ctx, msg)
        }
    }
}