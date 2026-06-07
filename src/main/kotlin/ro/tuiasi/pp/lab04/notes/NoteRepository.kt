package ro.tuiasi.pp.lab04.notes

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

// ─── Model ────────────────────────────────────────────────────────────────────

data class Note(
    val id: String,
    val author: String,
    val createdAt: LocalDateTime,
    val content: String
)

// ─── Interfață repository (DIP + ISP) ────────────────────────────────────────

interface NoteRepository {
    fun save(note: Note)
    fun findById(id: String): Note?
    fun findAll(): List<Note>
    fun delete(id: String)
}

// ─── Implementare pe fișier (SRP — persistența e responsabilitatea acestei clase) ─

/**
 * Stochează fiecare notiță ca fișier text `{id}.txt` în directorul `dir`.
 *
 * Format fișier:
 *   AUTHOR: <autor>
 *   CREATED_AT: <ISO LocalDateTime>
 *   CONTENT:
 *   <conținut multiliniar>
 */
class FileNoteRepository(private val dir: Path) : NoteRepository {

    init {
        dir.toFile().mkdirs()   // asigurăm că directorul există
    }

    override fun save(note: Note) {
        val text = buildString {
            appendLine("AUTHOR: ${note.author}")
            appendLine("CREATED_AT: ${note.createdAt}")
            appendLine("CONTENT:")
            append(note.content)
        }
        dir.resolve("${note.id}.txt").toFile().writeText(text)
    }

    override fun findById(id: String): Note? {
        val file = dir.resolve("$id.txt").toFile()
        return if (file.exists()) parseFile(id, file.readText()) else null
    }

    override fun findAll(): List<Note> =
        dir.toFile()
            .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.mapNotNull { f ->
                runCatching { parseFile(f.nameWithoutExtension, f.readText()) }.getOrNull()
            } ?: emptyList()

    override fun delete(id: String) {
        dir.resolve("$id.txt").toFile().delete()
    }

    // ─── Parsare fișier ───────────────────────────────────────────────────────

    private fun parseFile(id: String, text: String): Note {
        val lines = text.lines()

        val author = lines
            .firstOrNull { it.startsWith("AUTHOR:") }
            ?.removePrefix("AUTHOR:")?.trim() ?: ""

        val createdAt = lines
            .firstOrNull { it.startsWith("CREATED_AT:") }
            ?.removePrefix("CREATED_AT:")?.trim()
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: LocalDateTime.now()

        val contentStart = lines.indexOfFirst { it == "CONTENT:" }
        val content = if (contentStart >= 0 && contentStart + 1 < lines.size) {
            lines.drop(contentStart + 1).joinToString("\n")
        } else ""

        return Note(id, author, createdAt, content)
    }
}

// ─── Manager business (SRP — logica de business separată de persistență) ─────

class NoteManager(private val repo: NoteRepository) {

    /**
     * Creează o notiță nouă cu UUID generat automat și o salvează în repo.
     */
    fun createNote(author: String, content: String): Note {
        val note = Note(
            id        = UUID.randomUUID().toString(),
            author    = author,
            createdAt = LocalDateTime.now(),
            content   = content
        )
        repo.save(note)
        return note
    }

    /**
     * Încarcă o notiță după ID; aruncă NoSuchElementException dacă nu există.
     */
    fun loadNote(id: String): Note =
        repo.findById(id) ?: throw NoSuchElementException("Notița cu id=$id nu există")

    /**
     * Returnează toate notițele din repo.
     */
    fun listNotes(): List<Note> = repo.findAll()

    /**
     * Șterge notița cu ID-ul dat.
     */
    fun deleteNote(id: String) = repo.delete(id)
}

// ─── CLI Menu (SRP — UI-ul este responsabilitatea acestei clase) ──────────────

class CliMenu(private val manager: NoteManager) {

    fun run() {
        while (true) {
            println("\n=== Manager Notițe ===")
            println("1. Listare notițe")
            println("2. Încărcare notiță")
            println("3. Creare notiță")
            println("4. Ștergere notiță")
            println("0. Ieșire")
            print("Alegeți opțiunea: ")

            when (readLine()?.trim()) {
                "1" -> {
                    val notes = manager.listNotes()
                    if (notes.isEmpty()) println("Nu există notițe salvate.")
                    else notes.forEach { println("[${it.id.take(8)}...] ${it.author} — ${it.createdAt}") }
                }
                "2" -> {
                    print("ID notiță: ")
                    val id = readLine()?.trim() ?: continue
                    try {
                        val note = manager.loadNote(id)
                        println("─────────────────────────")
                        println("Autor:   ${note.author}")
                        println("Data:    ${note.createdAt}")
                        println("Conținut:\n${note.content}")
                        println("─────────────────────────")
                    } catch (e: NoSuchElementException) {
                        println("Notița nu a fost găsită.")
                    }
                }
                "3" -> {
                    print("Autor: ")
                    val author = readLine()?.trim() ?: continue
                    print("Conținut: ")
                    val content = readLine()?.trim() ?: continue
                    val note = manager.createNote(author, content)
                    println("Notiță creată cu id: ${note.id}")
                }
                "4" -> {
                    print("ID notiță de șters: ")
                    val id = readLine()?.trim() ?: continue
                    manager.deleteNote(id)
                    println("Notiță ștearsă.")
                }
                "0" -> {
                    println("La revedere!")
                    return
                }
                else -> println("Opțiune invalidă. Alege 0-4.")
            }
        }
    }
}
