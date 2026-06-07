package ro.tuiasi.pp.lab04.cinema

// ─── Modele de date ───────────────────────────────────────────────────────────

data class Movie(val id: Int, val title: String, val duration: Int)

data class Seat(val row: Int, val number: Int, val isAvailable: Boolean)

data class Ticket(val movie: Movie, val seat: Seat, val price: Double)

// ─── Interfață repository (ISP + DIP) ────────────────────────────────────────

interface CinemaRepository {
    fun addMovie(movie: Movie)
    fun findMovie(id: Int): Movie?
    fun addSeat(seat: Seat)
    fun findSeat(row: Int, number: Int): Seat?
    fun updateSeat(seat: Seat)
    fun saveTicket(ticket: Ticket)
    fun findTicket(movie: Movie, seat: Seat): Ticket?
    fun deleteTicket(ticket: Ticket)
    fun allSeats(movie: Movie): List<Seat>
}

// ─── Implementare in-memory (LSP — substituibilă cu orice altă implementare) ──

class InMemoryCinemaRepository : CinemaRepository {

    private val movies  = mutableMapOf<Int, Movie>()
    private val seats   = mutableMapOf<Pair<Int, Int>, Seat>()   // (row, number) → Seat
    private val tickets = mutableListOf<Ticket>()

    override fun addMovie(movie: Movie) {
        movies[movie.id] = movie
    }

    override fun findMovie(id: Int): Movie? = movies[id]

    override fun addSeat(seat: Seat) {
        seats[seat.row to seat.number] = seat
    }

    override fun findSeat(row: Int, number: Int): Seat? = seats[row to number]

    override fun updateSeat(seat: Seat) {
        seats[seat.row to seat.number] = seat
    }

    override fun saveTicket(ticket: Ticket) {
        tickets.add(ticket)
    }

    override fun findTicket(movie: Movie, seat: Seat): Ticket? =
        tickets.find { it.movie.id == movie.id && it.seat.row == seat.row && it.seat.number == seat.number }

    override fun deleteTicket(ticket: Ticket) {
        tickets.remove(ticket)
    }

    override fun allSeats(movie: Movie): List<Seat> = seats.values.toList()
}

// ─── Serviciu business (SRP — logica business separată de persistență) ────────

class CinemaService(private val repo: CinemaRepository) {

    /**
     * Rezervă un bilet pentru filmul și scaunul dat.
     * Aruncă IllegalStateException dacă scaunul nu este disponibil.
     */
    fun bookTicket(movie: Movie, seat: Seat, price: Double): Ticket {
        // Citim starea curentă a scaunului din repo (poate fi mai recentă decât parametrul)
        val current = repo.findSeat(seat.row, seat.number) ?: seat
        if (!current.isAvailable) {
            throw IllegalStateException(
                "Scaunul rândul ${seat.row}, numărul ${seat.number} nu este disponibil"
            )
        }
        val ticket = Ticket(movie, current, price)
        repo.updateSeat(current.copy(isAvailable = false))
        repo.saveTicket(ticket)
        return ticket
    }

    /**
     * Anulează un bilet: eliberează scaunul și șterge biletul din sistem.
     */
    fun cancelTicket(ticket: Ticket) {
        repo.updateSeat(ticket.seat.copy(isAvailable = true))
        repo.deleteTicket(ticket)
    }

    /**
     * Returnează lista scaunelor disponibile pentru filmul dat.
     */
    fun availableSeats(movie: Movie): List<Seat> =
        repo.allSeats(movie).filter { it.isAvailable }
}
