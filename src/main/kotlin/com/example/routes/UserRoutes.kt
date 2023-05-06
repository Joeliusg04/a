package com.example.routes

import com.example.models.*
import com.example.models.CRUD.*
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

fun Route.userRouting() {
    val userCrud = UserCRUD()
    val favCrud = FavouriteCRUD()
    val treasureCrud = TreasureCRUD()
    val gameCrud = GameCRUD()
    val reviewCrud = ReviewCRUD()
    val reportCrud = ReportCRUD()
    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ss")

    route("/user") {
        post("/login") {
            // recibimos este formato username,password
            val userCredentials = call.receive<String>()
            val userName = userCredentials.split(",")[0]
            val password = userCredentials.split(",")[1]

            if (userName == ""){
                return@post call.respondText("Username can't be empty.",
                    status = HttpStatusCode.BadRequest)
            }
            if (password == ""){
                return@post call.respondText("Password can't be empty.",
                    status = HttpStatusCode.BadRequest)
            }
            val userLogging = userCrud.selectUserByUserName(userName)
            if (userLogging != null) {
                if (userLogging.password == password){
                    return@post call.respondText("idUser: ${userLogging.idUser}",
                        status = HttpStatusCode.OK
                    )
                } else return@post call.respondText("Incorrect password.",
                        status = HttpStatusCode.Unauthorized
                )
            } else return@post call.respondText("User with username $userName not found.",
                status = HttpStatusCode.NotFound)
        }

        post {
            val newUser = call.receive<User>()
            val userIfExist = userCrud.checkIfUserExistByNick(newUser.nickName)
            if (userIfExist) {
                userCrud.addNewUser(newUser.nickName, newUser.email, newUser.password)
                call.respondText("User stored correctly.", status = HttpStatusCode.Created)
            } else call.respondText("User already exist.", status = HttpStatusCode.OK)
        }

        get("{userID}"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            val user = userCrud.selectUserByID(userID.toInt())
            if (user != null){
                return@get call.respond(user)
            } else return@get call.respondText("User with id $userID not found.",
                status = HttpStatusCode.NotFound)
        }

        put("{userID}"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@put call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)

            val userData = call.receiveMultipart()
            var userToUpdate = User(userID.toInt(), "", "", "", "","",
                "", listOf())
            val gson = Gson()
            userData.forEachPart { part ->
                when (part){
                    is PartData.FormItem -> {
                        userToUpdate = gson.fromJson(part.value, User::class.java)
                    }
                    is PartData.FileItem -> {
                        try {
                            userToUpdate.photo = part.originalFileName as String
                            if (userToUpdate.photo != userCrud.selectUserByID(userID.toInt())!!.photo){
                                val fileBytes = part.streamProvider().readBytes()
                                File("src/main/kotlin/com/example/user_pictures/"+userToUpdate.photo)
                                    .writeBytes(fileBytes)
                            }
                        } catch (e: FileNotFoundException){
                            println("Error ${e.message}")
                        }
                    }
                    else -> {}
                }
                println("Subido ${part.name}")
            }

            val favouriteTreasuresID = favCrud.selectAllFavouritesByUserID(userID.toInt())
            val favTreasures = mutableListOf<Treasures>()

            favouriteTreasuresID.forEach { fav ->
                favTreasures.add(treasureCrud.selectTreasureByID(fav.idTreasure)!!)
            }
            userToUpdate.favs = favTreasures

            userCrud.updateUser(userToUpdate)
            return@put call.respondText(
                "User with id $userID has been updated.", status = HttpStatusCode.Accepted
            )
        }
        delete("{userID}"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@delete call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)

            // Primero eliminamos los favoritos, los games (las reviews) y los reports
            val favList = favCrud.selectAllFavouritesByUserID(userID.toInt())
            favList.forEach { fav ->
                favCrud.deleteFavourite(userID.toInt(), fav.idTreasure)
            }
            // Eliminamos lo asociado al user y luego eliminamos el user
            reportCrud.deleteReportsOfUser(userID.toInt())
            reviewCrud.deleteReviewsOfUser(userID.toInt())
            gameCrud.deleteUserGames(userID.toInt())
            userCrud.deleteUser(userID.toInt())
            call.respondText("User with id $userID has been deleted.",
                status = HttpStatusCode.OK)

        }

        get("username/{userName}"){
            val userName = call.parameters["userName"]
            if (userName.isNullOrBlank()) return@get call.respondText("Missing username.",
                status = HttpStatusCode.BadRequest)

            val user = userCrud.selectUserByUserName(userName)
            if (user != null){
                return@get call.respond(user)
            } else return@get call.respondText("User with username $userName not found.",
                status = HttpStatusCode.NotFound)
        }

        get("{userID}/picture"){
            var userImage: File = File("")
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)

            val userPhoto = userCrud.selectUserByID(userID.toInt())!!.photo
            userImage = File("src/main/kotlin/com/example/user_pictures/$userPhoto")
            if (userImage.exists()){
                call.respondFile(userImage)
            } else {
                call.respondText("No image found.", status = HttpStatusCode.NotFound)
            }
        }

        get("{userID}/stats"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)

            var totalSolved = 0
            var totalNotSolved = 0

            val listOfGames = gameCrud.selectAllUserGames(userID.toInt())
            if (listOfGames.isNotEmpty()){
                for (i in listOfGames.indices){
                    if (listOfGames[i].solved) totalSolved++
                    else totalNotSolved++
                }

                val reportQuantity = reportCrud.selectAllReportByUserId(userID.toInt()).size

                var diff = 0L
                listOfGames.forEach { game ->
                    val startTime = LocalDateTime.parse(game.timeStart, formatter)
                    println(startTime)
                    val endTime = LocalDateTime.parse(game.timeEnd, formatter)
                    diff += Duration.between(startTime,endTime).toMillis()
                }

                val hours = TimeUnit.MILLISECONDS.toHours(diff/listOfGames.size)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff/listOfGames.size) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diff/listOfGames.size) % 60

                val averageTime = "$hours:$minutes:$seconds"

                val userStats = UserStats(userID.toInt(), totalSolved, totalNotSolved, reportQuantity, averageTime)
                call.respond(userStats)
            } else call.respond(UserStats(userID.toInt(),0,0,0,"00:00:00",))

        }

        get("{userID}/reviews") {
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText(
                "Missing user id.", status = HttpStatusCode.BadRequest)
            val reviews = reviewCrud.selectAllTreasureReviewsByUser(userID.toInt())
            if (reviews.isNotEmpty()) {
                call.respond(reviews)
            } else call.respondText(
                "User with id $userID hasn't made any reviews yet.", status = HttpStatusCode.NotFound)
        }
        get("{userID}/reviews/{reviewID}") {
            val userID = call.parameters["userID"]
            val reviewID = call.parameters["reviewID"]

            if (userID.isNullOrBlank()) return@get call.respondText(
                "Missing user id.", status = HttpStatusCode.BadRequest)
            if (reviewID.isNullOrBlank()) return@get call.respondText(
                "Missing review id.", status = HttpStatusCode.BadRequest)

            val review = reviewCrud.selectReviewByUser(userID.toInt(), reviewID.toInt())
            if (review != null) {
                call.respond(review)
            } else call.respondText("User with id $userID and review with id $reviewID not found.",
                status = HttpStatusCode.NotFound)
        }

        get("{userID}/reports") {
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText(
                "Missing user id.", status = HttpStatusCode.BadRequest)
            val reports = reportCrud.selectAllTreasureReports(userID.toInt())
            if (reports.isNotEmpty()) {
                call.respond(reports)
            } else call.respondText("User with id $userID hasn't made any reports yet.",
                status = HttpStatusCode.Accepted)
        }

        get("{userID}/reports/{reportID}") {
            val userID = call.parameters["userID"]
            val reportID = call.parameters["reportID"]

            if (userID.isNullOrBlank()) return@get call.respondText(
                "Missing user id.", status = HttpStatusCode.BadRequest
            )
            if (reportID.isNullOrBlank()) return@get call.respondText(
                "Missing report id.", status = HttpStatusCode.BadRequest
            )
            val report = reportCrud.selectTreasureReportByUserID(userID.toInt(), reportID.toInt())
            if (report != null) {
                call.respond(report)
            } else call.respondText(
                "Report with id $report made by user with id $userID not found.",
                status = HttpStatusCode.NotFound
            )
        }


        get("{userID}/treasures"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            val treasuresSolved = treasureCrud.selectAllTreasuresSolvedByUser(userID.toInt())
            if (treasuresSolved.isNotEmpty()){
                call.respond(treasuresSolved)
            } else call.respondText("User with id $userID hasn't solved any treasures yet.",
                status = HttpStatusCode.Accepted)
        }


        get("{userID}/favs"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            val favList = favCrud.selectAllFavouritesByUserID(userID.toInt())
            if (favList.isNotEmpty()){
                call.respond(favList)
            } else call.respondText("User with id $userID hasn't mark any treasure as favourite.",
                status = HttpStatusCode.Accepted)
        }

        get("{userID}/favs/{treasureID}"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@get call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            val treasureID = call.parameters["treasureID"]
            if (treasureID.isNullOrBlank()) return@get call.respondText("Missing treasure id.",
                status = HttpStatusCode.BadRequest)
            call.respond(favCrud.checkIfFavourite(userID.toInt(), treasureID.toInt()))

        }
        post("{userID}/favs"){
            val userID = call.parameters["userID"]
            if (userID.isNullOrBlank()) return@post call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            val treasureID = call.receive<Treasures>().idTreasure
            favCrud.addFavourite(userID.toInt(), treasureID)
            call.respondText("Treasure with id $treasureID added to user with id $userID list of favorites.")
        }
        delete("{userID}/favs/{treasureID}"){
            val userID = call.parameters["userID"]
            val treasureID = call.parameters["treasureID"]
            if (userID.isNullOrBlank()) return@delete call.respondText("Missing user id.",
                status = HttpStatusCode.BadRequest)
            if (treasureID.isNullOrBlank()) return@delete call.respondText("Missing treasure id.",
                status = HttpStatusCode.BadRequest)
            favCrud.deleteFavourite(userID.toInt(), treasureID.toInt())
            call.respondText("Treasure with id $treasureID deleted from user with id $userID list of favorites.",
                status = HttpStatusCode.OK)
        }

        delete("{userID}/reports/{reportID}"){
            val userID = call.parameters["userID"]
            val reportID = call.parameters["reportID"]
            if (userID.isNullOrBlank()) return@delete call.respondText(
                "Missing user id.", status = HttpStatusCode.BadRequest
            )
            if (reportID.isNullOrBlank()) return@delete call.respondText(
                "Missing report id.", status = HttpStatusCode.BadRequest
            )
            reportCrud.deleteReport(reportID.toInt())
            call.respondText(
                "Report with id $reportID of user with id $userID has been deleted.",
                status = HttpStatusCode.OK
            )
        }

    }

}