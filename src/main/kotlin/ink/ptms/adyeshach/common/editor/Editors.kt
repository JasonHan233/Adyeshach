package ink.ptms.adyeshach.common.editor

import ink.ptms.adyeshach.Adyeshach
import ink.ptms.adyeshach.common.entity.EntityInstance
import ink.ptms.adyeshach.common.entity.EntityMetaable
import ink.ptms.adyeshach.common.entity.type.AdyEntityLiving
import io.izzel.taboolib.module.i18n.I18n
import io.izzel.taboolib.module.tellraw.TellrawJson
import io.izzel.taboolib.util.book.BookFormatter
import io.izzel.taboolib.util.chat.ComponentSerializer
import io.izzel.taboolib.util.item.ItemBuilder
import io.izzel.taboolib.util.item.inventory.MenuBuilder
import io.izzel.taboolib.util.lite.Materials
import io.izzel.taboolib.util.lite.Signs
import org.bukkit.Color
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.material.MaterialData
import org.bukkit.util.NumberConversions
import java.lang.NullPointerException
import kotlin.reflect.KClass

object Editors {

    private val cacheEnums = HashMap<String, Array<out Any>>()
    private val cacheEquipment = HashMap<EquipmentSlot, EntityMetaable.MetaEditor>()

    fun getEnums(enum: KClass<*>): Array<out Any> {
        return cacheEnums.computeIfAbsent(enum.java.name) { enum.java.enumConstants }
    }

    val TEXT = EntityMetaable.MetaEditor()
            .modify { player, entity, meta ->
                Signs.fakeSign(player, arrayOf("${entity.getMetadata<Any>(meta.key)}", "", "请在第一行输入内容")) {
                    if (it[0].isNotEmpty()) {
                        entity.setMetadata(meta.key, "${it[0]}${it[1]}".replace("&", "§"))
                    }
                    Editor.open(player, entity)
                }
            }
            .display { _, entity, meta ->
                val display = entity.getMetadata<Any>(meta.key).toString()
                if (display.isEmpty()) "§7_" else Editor.toSimple(display)
            }

    val COLOR = EntityMetaable.MetaEditor()
            .modify { player, entity, meta ->
                Signs.fakeSign(player, arrayOf(meta.editor!!.onDisplay!!.invoke(player, entity, meta), "", "请在第一行输入内容")) {
                    if (it[0].isNotEmpty()) {
                        val v = it[0].split("-").map { a -> NumberConversions.toInt(a) }
                        entity.setMetadata(meta.key, Color.fromRGB(v.getOrElse(0) { 0 }, v.getOrElse(1) { 0 }, v.getOrElse(2) { 0 }).asRGB())
                    }
                    Editor.open(player, entity)
                }
            }
            .display { _, entity, meta ->
                val color = Color.fromRGB(entity.getMetadata(meta.key))
                if (Editor.version >= 11600) {
                    "${io.izzel.taboolib.util.chat.ChatColor.of(java.awt.Color(color.red, color.green, color.blue))}${color.red}-${color.green}-${color.blue}"
                } else {
                    "${color.red}-${color.green}-${color.blue}"
                }
            }

    val ITEM = EntityMetaable.MetaEditor()
            .modify { player, entity, meta ->
                MenuBuilder.builder(Adyeshach.plugin)
                        .title("Adyeshach Editor : Input")
                        .rows(1)
                        .items("####@####")
                        .put('#', ItemBuilder(Materials.BLACK_STAINED_GLASS_PANE.parseItem()).name("§f").build())
                        .put('@', meta.def as ItemStack)
                        .event {
                            if (it.slot == '#') {
                                it.isCancelled = true
                            }
                        }.close {
                            entity.setMetadata(meta.key, it.inventory.getItem(4) ?: ItemStack(Material.AIR))
                            Editor.open(player, entity)
                        }.open(player)
            }
            .display { player, entity, meta ->
                I18n.get().getName(player, entity.getMetadata<ItemStack>(meta.key))
            }

    val MATERIAL_DATA = EntityMetaable.MetaEditor()
            .modify { player, entity, meta ->
                MenuBuilder.builder(Adyeshach.plugin)
                        .title("Adyeshach Editor : Input")
                        .rows(1)
                        .items("####@####")
                        .put('#', ItemBuilder(Materials.BLACK_STAINED_GLASS_PANE.parseItem()).name("§f").build())
                        .put('@', (meta.def as MaterialData).toItemStack(1))
                        .event {
                            if (it.slot == '#') {
                                it.isCancelled = true
                            }
                        }.close {
                            try {
                                entity.setMetadata(meta.key, (it.inventory.getItem(4) ?: ItemStack(Material.AIR)).data!!)
                            } catch (t: Throwable) {
                                t.printStackTrace()
                            }
                            Editor.open(player, entity)
                        }.open(player)
            }
            .display { player, entity, meta ->
                I18n.get().getName(player, entity.getMetadata<MaterialData>(meta.key).toItemStack(1))
            }

    fun enums(enum: KClass<*>, command: (Player, EntityInstance, EntityMetaable.Meta, Int, Any) -> (String)): EntityMetaable.MetaEditor {
        return EntityMetaable.MetaEditor()
                .modify { player, entity, meta ->
                    val book = BookFormatter.writtenBook()
                    var page = TellrawJson.create()
                    var i = 0
                    getEnums(enum).forEachIndexed { index, e ->
                        if (e is DyeColor && Editor.version >= 11600) {
                            page.append("  ${io.izzel.taboolib.util.chat.ChatColor.of(java.awt.Color(e.color.red, e.color.green, e.color.blue))}${Editor.toSimple(e.toString())}")
                        } else {
                            page.append("  ${Editor.toSimple(e.toString())}")
                        }
                        page.clickCommand(command.invoke(player, entity, meta, index, e)).hoverText("§nClick To Select\n§7$e").newLine()
                        if (++i == 12) {
                            i = 0
                            book.addPages(ComponentSerializer.parse(page.toRawMessage(player)))
                            page = TellrawJson.create()
                        }
                    }
                    if (i > 0) {
                        book.addPages(ComponentSerializer.parse(page.toRawMessage(player)))
                    }
                    book.open(player)
                }.toEnum(enum)
    }

    fun equip(equipmentSlot: EquipmentSlot): EntityMetaable.MetaEditor {
        return cacheEquipment.computeIfAbsent(equipmentSlot) {
            EntityMetaable.MetaEditor()
                    .reset { entity, meta ->
                        (entity as AdyEntityLiving).setEquipment(equipmentSlot, ItemStack(Material.AIR))
                    }
                    .modify { player, entity, meta ->
                        MenuBuilder.builder(Adyeshach.plugin)
                                .title("Adyeshach Editor : Input")
                                .rows(1)
                                .items("####@####")
                                .put('#', ItemBuilder(Materials.BLACK_STAINED_GLASS_PANE.parseItem()).name("§f").build())
                                .put('@', (entity as AdyEntityLiving).getEquipment(equipmentSlot) ?: ItemStack(Material.AIR))
                                .event {
                                    if (it.slot == '#') {
                                        it.isCancelled = true
                                    }
                                }.close {
                                    entity.setEquipment(equipmentSlot, it.inventory.getItem(4) ?: ItemStack(Material.AIR))
                                    Editor.open(player, entity)
                                }.open(player)
                    }
                    .display { player, entity, meta ->
                        try {
                            I18n.get().getName(player, (entity as AdyEntityLiving).getEquipment(equipmentSlot) ?: ItemStack(Material.AIR))
                        } catch (ignored: NullPointerException) {
                            "NULL"
                        }
                    }
        }
    }
}