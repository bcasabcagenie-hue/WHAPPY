import SceneKit
import UIKit

/// Native tabletop renderer shared by every WAPI game on iPhone and iPad.
/// Models are assembled from real SceneKit meshes so lighting, shadows and
/// camera movement remain physical instead of looking like a flat mock-up.
enum WapiGameSceneKit {
    private enum PoolPhysicsCategory {
        static let ball = 1 << 1
        static let pocket = 1 << 2
        static let rail = 1 << 3
    }

    /** SceneKit contact bridge retained by the scene itself. It turns visual
     * pockets into real sensors and drives contact audio from actual physics. */
    private final class PoolPhysicsDelegate: NSObject, SCNPhysicsContactDelegate {
        private var lastBallSoundAt = 0.0
        private var lastRailSoundAt = 0.0
        private var pocketedNodes = Set<ObjectIdentifier>()
        weak var poolScene: PoolScene?
        var scratched = false
        var firstContact: Int?
        var pocketedIDs = Set<Int>()
        func beginShot() { scratched = false; firstContact = nil; pocketedIDs = [] }

        func physicsWorld(_ world: SCNPhysicsWorld, didBegin contact: SCNPhysicsContact) {
            let a = contact.nodeA
            let b = contact.nodeB
            let aCategory = a.physicsBody?.categoryBitMask ?? 0
            let bCategory = b.physicsBody?.categoryBitMask ?? 0
            let categories = aCategory | bCategory
            if categories & PoolPhysicsCategory.pocket != 0,
               categories & PoolPhysicsCategory.ball != 0 {
                let ball = aCategory == PoolPhysicsCategory.ball ? a : b
                let pocket = aCategory == PoolPhysicsCategory.pocket ? a : b
                pocketBall(ball, into: pocket)
                return
            }
            let now = ProcessInfo.processInfo.systemUptime
            if aCategory == PoolPhysicsCategory.ball && bCategory == PoolPhysicsCategory.ball {
                if firstContact == nil {
                    if a.name == "wapi.pool.ball.0" { firstContact = b.name?.split(separator: ".").last.flatMap { Int($0) } }
                    else if b.name == "wapi.pool.ball.0" { firstContact = a.name?.split(separator: ".").last.flatMap { Int($0) } }
                }
                guard now - lastBallSoundAt > 0.045 else { return }
                lastBallSoundAt = now
                let energy = min(0.68, max(0.18, CGFloat(contact.collisionImpulse) * 0.10))
                DispatchQueue.main.async { WapiSounds.gamePoolCollision(Float(energy)) }
            } else if categories & PoolPhysicsCategory.rail != 0,
                      categories & PoolPhysicsCategory.ball != 0 {
                guard now - lastRailSoundAt > 0.065 else { return }
                lastRailSoundAt = now
                let energy = min(0.55, max(0.14, CGFloat(contact.collisionImpulse) * 0.09))
                DispatchQueue.main.async { WapiSounds.gamePoolRail(Float(energy)) }
            }
        }

        private func pocketBall(_ ball: SCNNode, into pocket: SCNNode) {
            let identifier = ObjectIdentifier(ball)
            guard pocketedNodes.insert(identifier).inserted else { return }
            let ballID = ball.name?.split(separator: ".").last.flatMap({ Int($0) }) ?? -1
            if ballID >= 0 {
                if ballID == 0 { scratched = true } else { pocketedIDs.insert(ballID) }
            }
            let originalBody = ball.physicsBody
            originalBody?.clearAllForces()
            ball.physicsBody = nil
            DispatchQueue.main.async { WapiSounds.gamePoolPocket() }
            let lip = SCNVector3(pocket.position.x, 0.19, pocket.position.z)
            let throat = SCNVector3(pocket.position.x, -0.34, pocket.position.z)
            let roll = SCNAction.move(to: lip, duration: 0.10)
            roll.timingMode = .easeIn
            let drop = SCNAction.group([
                .move(to: throat, duration: 0.22),
                .scale(to: 0.22, duration: 0.22),
                .rotateBy(x: .pi * 1.25, y: .pi * 0.85, z: .pi * 0.35, duration: 0.22),
            ])
            drop.timingMode = .easeIn
            let pocketPosition = pocket.position
            ball.runAction(.sequence([roll, drop])) {
                if ball.name == "wapi.pool.ball.0" {
                    ball.position = SCNVector3(-1.65, 0.40, 0)
                    ball.scale = SCNVector3(1, 1, 1)
                    self.pocketedNodes.remove(identifier)
                    ball.physicsBody = originalBody
                    originalBody?.clearAllForces()
                } else {
                    self.poolScene?.recordPocketedBall(id: ballID, from: pocketPosition)
                    ball.removeFromParentNode()
                }
            }
        }
    }

    private final class PoolScene: SCNScene {
        var retainedContactDelegate: PoolPhysicsDelegate?
        private var returnedBallIDs: [Int] = []

        func recordPocketedBall(id: Int, from source: SCNVector3) {
            guard id > 0, !returnedBallIDs.contains(id), returnedBallIDs.count < 15 else { return }
            returnedBallIDs.append(id)
            let ball = WapiGameSceneKit.makePoolReturnBall(id: id)
            let slot = returnedBallIDs.count - 1
            let lane = slot / 5
            let laneSlot = slot % 5
            let destination = SCNVector3(-1.60 + Float(laneSlot) * 0.80, 0.46, -2.94 + Float(lane) * 0.20)
            ball.position = SCNVector3(source.x, 0.05, source.z)
            rootNode.addChildNode(ball)
            let route = SCNAction.sequence([
                .move(to: SCNVector3(source.x, 0.18, source.z), duration: 0.08),
                .move(to: destination, duration: 0.34),
                .rotateBy(x: .pi * 1.3, y: .pi * 1.1, z: .pi * 0.2, duration: 0.34),
            ])
            route.timingMode = .easeOut
            ball.runAction(route)
        }
    }

    static func makeScene(named game: String, dieValue: Int) -> SCNScene {
        let scene = PoolScene()
        configureStage(scene, wide: game == "Billard WAPI")
        if game == "Billard WAPI" { scene.physicsWorld.gravity = SCNVector3Zero }
        switch game {
        case "Ludo WAPI": addLudo(to: scene, dieValue: dieValue)
        case "Billard WAPI": addPool(to: scene)
        case "Échecs WAPI": addStrategyBoard(to: scene, chess: true)
        case "Jeu de dames": addStrategyBoard(to: scene, chess: false)
        default: addCards(to: scene, poker: game == "Poker WAPI")
        }
        return scene
    }

    static func animateDie(in scene: SCNScene?, value: Int) {
        guard let die = scene?.rootNode.childNode(withName: "wapi.game.die", recursively: true) else { return }
        die.removeAllActions()
        let target = dieRotation(for: value)
        let spin = SCNAction.rotateBy(x: .pi * 2.0, y: .pi * 3.0, z: .pi * 2.0, duration: 0.55)
        spin.timingMode = .easeInEaseOut
        let hop = SCNAction.sequence([
            .moveBy(x: 0, y: 0.55, z: 0, duration: 0.20),
            .moveBy(x: 0, y: -0.55, z: 0, duration: 0.32),
        ])
        hop.timingMode = .easeInEaseOut
        die.runAction(.group([spin, hop])) {
            DispatchQueue.main.async {
                die.runAction(.rotateTo(x: CGFloat(target.x), y: CGFloat(target.y), z: CGFloat(target.z), duration: 0.18, usesShortestUnitArc: true))
            }
        }
    }

    static func setPoolCue(in scene: SCNScene?, angle: Float, power: Int, sideSpin: Float = 0, followSpin: Float = 0) {
        guard let scene,
              let cue = scene.rootNode.childNode(withName: "wapi.game.cue", recursively: true),
              let white = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: true) else { return }
        let pullBack = 0.34 + Float(min(max(power, 10), 100)) / 100 * 0.58
        cue.position = SCNVector3(
            white.position.x - cos(angle) * (1.85 + pullBack),
            0.48,
            white.position.z - sin(angle) * (1.85 + pullBack)
        )
        cue.eulerAngles = SCNVector3(0, -angle, -Float.pi / 2)
        cue.isHidden = false
        let marker: SCNNode
        if let existing = white.childNode(withName: "wapi.pool.contact", recursively: false) {
            marker = existing
        } else {
            let geometry = SCNSphere(radius: 0.022)
            geometry.firstMaterial = material(color: .systemRed, metalness: 0.05, roughness: 0.18)
            marker = SCNNode(geometry: geometry)
            marker.name = "wapi.pool.contact"
            white.addChildNode(marker)
        }
        let clampedSide = min(max(sideSpin, -1), 1)
        let clampedFollow = min(max(followSpin, -1), 1)
        marker.position = SCNVector3(clampedSide * 0.078, 0.132, -clampedFollow * 0.078)
        marker.isHidden = false
        updatePoolGuide(in: scene, cueBall: white, angle: angle, sideSpin: clampedSide, followSpin: clampedFollow)
    }

    static func strikePool(in scene: SCNScene?, angle: Float, power: Int, sideSpin: Float = 0, followSpin: Float = 0) {
        guard let scene,
              let white = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: true) else { return }
        (scene as? PoolScene)?.retainedContactDelegate?.beginShot()
        let cue = scene.rootNode.childNode(withName: "wapi.game.cue", recursively: true)
        let advance = 0.34 + Float(min(max(power, 10), 100)) / 100 * 0.58
        let contact = SCNAction.run { _ in
        DispatchQueue.main.async { WapiSounds.gamePool(power: power) }
        // An impulse is mass × speed. Applying the desired speed directly as
        // an impulse made a 170 g ball launch six times too fast.
        let force = Float(white.physicsBody?.mass ?? 0.17) * (2.0 + Float(min(max(power, 10), 100)) / 100 * 5.4)
        white.physicsBody?.clearAllForces()
        white.physicsBody?.applyForce(SCNVector3(cos(angle) * force, 0, sin(angle) * force), asImpulse: true)
        // Torque creates genuine draw/follow and English in SceneKit's physics
        // solver; it is not a visual animation disconnected from the ball.
        let clampedSide = min(max(sideSpin, -1), 1)
        let clampedFollow = min(max(followSpin, -1), 1)
        if abs(clampedSide) > 0.01 {
            white.physicsBody?.applyTorque(SCNVector4(0, 1, 0, clampedSide * force * 0.07), asImpulse: true)
        }
        if abs(clampedFollow) > 0.01 {
            white.physicsBody?.applyTorque(SCNVector4(-sin(angle), 0, cos(angle), clampedFollow * force * 0.12), asImpulse: true)
        }
        white.childNode(withName: "wapi.pool.contact", recursively: false)?.isHidden = true
        scene.rootNode.childNode(withName: "wapi.game.cue", recursively: true)?.isHidden = true
        scene.rootNode.childNode(withName: "wapi.pool.guide", recursively: false)?.isHidden = true
        }
        let stroke = SCNAction.moveBy(x: CGFloat(cos(angle) * advance), y: 0, z: CGFloat(sin(angle) * advance), duration: 0.12)
        stroke.timingMode = .easeIn
        if let cue { cue.runAction(.sequence([stroke, contact]), forKey: "cue-contact") }
        else { white.runAction(contact) }
    }

    /** Professional aim preview calculated from the real ball centres. It
     * exposes the object-ball line, cue-ball tangent and one-cushion rebound;
     * changing English therefore changes the preview before the shot. */
    private static func updatePoolGuide(in scene: SCNScene, cueBall: SCNNode, angle: Float, sideSpin: Float, followSpin: Float) {
        scene.rootNode.childNode(withName: "wapi.pool.guide", recursively: false)?.removeFromParentNode()
        let guide = SCNNode()
        guide.name = "wapi.pool.guide"
        scene.rootNode.addChildNode(guide)
        let directionX = cos(angle)
        let directionZ = sin(angle)
        let cueX = cueBall.position.x
        let cueZ = cueBall.position.z
        let xLimit: Float = directionX > 0.0001 ? (3.08 - cueX) / directionX : directionX < -0.0001 ? (-3.08 - cueX) / directionX : 20
        let zLimit: Float = directionZ > 0.0001 ? (1.68 - cueZ) / directionZ : directionZ < -0.0001 ? (-1.68 - cueZ) / directionZ : 20
        var guideDistance = min(8.0, max(0.35, min(xLimit, zLimit)))
        var target: SCNNode?
        let objectBalls = scene.rootNode.childNodes { node, _ in
            guard let name = node.name, name.hasPrefix("wapi.pool.ball.") else { return false }
            return name != "wapi.pool.ball.0"
        }
        for candidate in objectBalls {
            let relX = candidate.position.x - cueX
            let relZ = candidate.position.z - cueZ
            let projection = relX * directionX + relZ * directionZ
            let perpendicularSquared = relX * relX + relZ * relZ - projection * projection
            let contactRadius: Float = 0.292
            if projection > 0.22, perpendicularSquared >= 0, perpendicularSquared <= contactRadius * contactRadius {
                let hit = projection - sqrt(max(0, contactRadius * contactRadius - perpendicularSquared))
                if hit > 0.18, hit < guideDistance { guideDistance = hit; target = candidate }
            }
        }
        let ghost = SCNVector3(cueX + directionX * guideDistance, 0.286, cueZ + directionZ * guideDistance)
        addDashedPoolLine(from: SCNVector3(cueX, 0.285, cueZ), to: ghost, color: UIColor(red: 0.70, green: 1, blue: 0.90, alpha: 0.84), to: guide)
        let ghostRing = SCNNode(geometry: SCNTorus(ringRadius: 0.155, pipeRadius: 0.016))
        ghostRing.geometry?.firstMaterial = emissivePoolGuideMaterial(UIColor(red: 0.70, green: 1, blue: 0.90, alpha: 0.86))
        ghostRing.position = ghost
        guide.addChildNode(ghostRing)
        if let target {
            let normalX = target.position.x - ghost.x
            let normalZ = target.position.z - ghost.z
            let length = max(0.001, sqrt(normalX * normalX + normalZ * normalZ))
            let objectX = normalX / length
            let objectZ = normalZ / length
            let targetStart = SCNVector3(target.position.x, 0.283, target.position.z)
            addDashedPoolLine(from: targetStart, to: SCNVector3(targetStart.x + objectX * 1.55, 0.283, targetStart.z + objectZ * 1.55), color: UIColor(red: 1, green: 0.68, blue: 0.15, alpha: 0.86), to: guide)
            let targetRing = SCNNode(geometry: SCNTorus(ringRadius: 0.158, pipeRadius: 0.014))
            targetRing.geometry?.firstMaterial = emissivePoolGuideMaterial(UIColor(red: 1, green: 0.62, blue: 0.10, alpha: 0.88))
            targetRing.position = targetStart
            guide.addChildNode(targetRing)
            let projection = directionX * objectX + directionZ * objectZ
            var tangentX = directionX - objectX * projection + objectX * followSpin * 0.42 - objectZ * sideSpin * 0.24
            var tangentZ = directionZ - objectZ * projection + objectZ * followSpin * 0.42 + objectX * sideSpin * 0.24
            let tangentLength = sqrt(tangentX * tangentX + tangentZ * tangentZ)
            if tangentLength > 0.025 {
                tangentX /= tangentLength; tangentZ /= tangentLength
                addDashedPoolLine(from: ghost, to: SCNVector3(ghost.x + tangentX * 1.25, ghost.y, ghost.z + tangentZ * 1.25), color: UIColor(red: 0.16, green: 0.76, blue: 1, alpha: 0.78), to: guide)
            }
        } else if guideDistance < 7.9 {
            let hitVerticalRail = xLimit <= zLimit
            let reboundX = hitVerticalRail ? -directionX : directionX
            let reboundZ = hitVerticalRail ? directionZ : -directionZ
            addDashedPoolLine(from: ghost, to: SCNVector3(ghost.x + reboundX * 1.70, ghost.y, ghost.z + reboundZ * 1.70), color: UIColor(red: 0.16, green: 0.72, blue: 1, alpha: 0.72), to: guide)
        }
    }

    private static func addDashedPoolLine(from start: SCNVector3, to end: SCNVector3, color: UIColor, to parent: SCNNode) {
        let dx = end.x - start.x
        let dz = end.z - start.z
        let length = sqrt(dx * dx + dz * dz)
        guard length > 0.05 else { return }
        let ux = dx / length
        let uz = dz / length
        let count = max(1, Int(length / 0.23))
        for index in 0..<count {
            let distance = min(length, 0.14 + Float(index) * 0.23)
            let dash = SCNBox(width: 0.12, height: 0.008, length: 0.026, chamferRadius: 0.006)
            dash.firstMaterial = emissivePoolGuideMaterial(color.withAlphaComponent(max(0.18, color.cgColor.alpha - CGFloat(index) * 0.018)))
            let node = SCNNode(geometry: dash)
            node.position = SCNVector3(start.x + ux * distance, start.y, start.z + uz * distance)
            node.eulerAngles.y = -atan2(uz, ux)
            parent.addChildNode(node)
        }
    }

    private static func emissivePoolGuideMaterial(_ color: UIColor) -> SCNMaterial {
        let result = material(color: color, metalness: 0.12, roughness: 0.18)
        result.emission.contents = color.withAlphaComponent(0.42)
        result.blendMode = .add
        result.isDoubleSided = true
        return result
    }

    private static func configureStage(_ scene: SCNScene, wide: Bool) {
        scene.background.contents = UIColor(red: 0.006, green: 0.018, blue: 0.038, alpha: 1)
        scene.fogColor = UIColor(red: 0.006, green: 0.018, blue: 0.038, alpha: 1)
        scene.fogStartDistance = 16
        scene.fogEndDistance = 29

        let camera = SCNNode()
        camera.name = "wapi.game.camera"
        camera.camera = SCNCamera()
        camera.camera?.fieldOfView = wide ? 43 : 40
        camera.camera?.wantsHDR = true
        camera.camera?.wantsExposureAdaptation = !wide
        camera.camera?.bloomIntensity = wide ? 0 : 0.14
        camera.camera?.bloomThreshold = 1.1
        camera.camera?.vignettingIntensity = 0.35
        camera.camera?.vignettingPower = 0.8
        camera.camera?.zNear = 0.05
        camera.camera?.zFar = 80
        camera.position = wide ? SCNVector3(0, 7.0, 9.8) : SCNVector3(0, 7.8, 8.5)
        camera.constraints = [lookAt(SCNVector3(0, 0.18, 0))]
        scene.rootNode.addChildNode(camera)

        let ambient = SCNNode()
        ambient.light = SCNLight()
        ambient.light?.type = .ambient
        ambient.light?.color = UIColor(red: 0.34, green: 0.42, blue: 0.56, alpha: 1)
        ambient.light?.intensity = wide ? 180 : 380
        scene.rootNode.addChildNode(ambient)

        let key = light(type: .spot, intensity: wide ? 850 : 1_650, color: UIColor(red: 1, green: 0.91, blue: 0.78, alpha: 1), position: SCNVector3(-4.5, 8.5, 5.5))
        key.light?.castsShadow = true
        key.light?.shadowMode = .deferred
        key.light?.shadowRadius = 7
        key.light?.shadowSampleCount = 24
        key.light?.shadowColor = UIColor.black.withAlphaComponent(0.52)
        key.light?.spotInnerAngle = 44
        key.light?.spotOuterAngle = 92
        key.constraints = [lookAt(SCNVector3(0, 0, 0))]
        scene.rootNode.addChildNode(key)

        let fill = light(type: .omni, intensity: wide ? 320 : 780, color: UIColor(red: 0.42, green: 0.68, blue: 1, alpha: 1), position: SCNVector3(5.5, 4.5, -3.8))
        scene.rootNode.addChildNode(fill)

        let floor = SCNFloor()
        floor.reflectivity = 0.16
        floor.reflectionFalloffEnd = 7
        floor.firstMaterial = material(color: UIColor(red: 0.014, green: 0.022, blue: 0.035, alpha: 1), roughness: 0.72)
        let floorNode = SCNNode(geometry: floor)
        floorNode.position.y = -0.62
        scene.rootNode.addChildNode(floorNode)
    }

    private static func addStrategyBoard(to scene: SCNScene, chess: Bool) {
        addBoardBase(to: scene, width: 6.72, length: 6.72)
        let lightSquare = material(color: UIColor(red: 0.84, green: 0.64, blue: 0.37, alpha: 1), roughness: 0.38)
        let darkSquare = material(color: UIColor(red: 0.18, green: 0.064, blue: 0.022, alpha: 1), roughness: 0.34)
        let cell: Float = 0.72
        for row in 0..<8 {
            for column in 0..<8 {
                let tile = SCNBox(width: CGFloat(cell * 0.99), height: 0.075, length: CGFloat(cell * 0.99), chamferRadius: 0.015)
                tile.firstMaterial = (row + column).isMultiple(of: 2) ? lightSquare : darkSquare
                let tileNode = SCNNode(geometry: tile)
                tileNode.position = boardPosition(row: row, column: column, cell: cell, y: 0.055)
                tileNode.castsShadow = true
                scene.rootNode.addChildNode(tileNode)
            }
        }

        if chess {
            let order = ["rook", "knight", "bishop", "queen", "king", "bishop", "knight", "rook"]
            for column in 0..<8 {
                addChessPiece(order[column], white: false, at: boardPosition(row: 0, column: column, cell: cell, y: 0.13), to: scene)
                addChessPiece("pawn", white: false, at: boardPosition(row: 1, column: column, cell: cell, y: 0.13), to: scene)
                addChessPiece("pawn", white: true, at: boardPosition(row: 6, column: column, cell: cell, y: 0.13), to: scene)
                addChessPiece(order[column], white: true, at: boardPosition(row: 7, column: column, cell: cell, y: 0.13), to: scene)
            }
        } else {
            for row in 0..<3 {
                for column in 0..<8 where !(row + column).isMultiple(of: 2) {
                    addChecker(white: false, king: row == 0 && column == 1, at: boardPosition(row: row, column: column, cell: cell, y: 0.12), to: scene)
                }
            }
            for row in 5..<8 {
                for column in 0..<8 where !(row + column).isMultiple(of: 2) {
                    addChecker(white: true, king: row == 7 && column == 6, at: boardPosition(row: row, column: column, cell: cell, y: 0.12), to: scene)
                }
            }
        }
    }

    static func frameStrategyCamera(in scene: SCNScene, viewport: CGSize) {
        guard viewport.width > 0, viewport.height > 0, let camera = scene.rootNode.childNodes.first(where: { $0.camera != nil }) else { return }
        let pitch = Float.pi * 49 / 180; let tangent = tan(Float.pi * 19 / 180)
        let aspect = Float(viewport.width / viewport.height)
        var radius: Float = 0
        for x: Float in [-3.5, 3.5] { for z: Float in [-3.5, 3.5] { for y: Float in [-0.5, 1.25] {
            radius = max(radius, max(abs(x) / (tangent * aspect), abs(y * cos(pitch) - z * sin(pitch)) / tangent) + y * sin(pitch) + z * cos(pitch))
        } } }
        camera.camera?.projectionDirection = .vertical; camera.camera?.fieldOfView = 38
        radius *= 1.04
        camera.position = SCNVector3(0, sin(pitch) * radius, cos(pitch) * radius)
        camera.look(at: SCNVector3Zero)
    }

    private static func addChecker(white: Bool, king: Bool, at position: SCNVector3, to scene: SCNScene) {
        let root = SCNNode()
        root.position = position
        let baseColor = white ? UIColor(red: 0.92, green: 0.88, blue: 0.76, alpha: 1) : UIColor(red: 0.035, green: 0.045, blue: 0.065, alpha: 1)
        let accent = white ? UIColor(red: 1, green: 0.97, blue: 0.87, alpha: 1) : UIColor(red: 0.14, green: 0.17, blue: 0.22, alpha: 1)
        root.addChildNode(shape(SCNCylinder(radius: 0.28, height: 0.12), color: baseColor, roughness: 0.18, y: 0.07))
        root.addChildNode(shape(SCNTorus(ringRadius: 0.225, pipeRadius: 0.038), color: accent, roughness: 0.14, y: 0.14))
        root.addChildNode(shape(SCNCylinder(radius: 0.215, height: 0.045), color: baseColor, roughness: 0.15, y: 0.17))
        root.addChildNode(shape(SCNTorus(ringRadius: 0.145, pipeRadius: 0.018), color: accent, roughness: 0.12, y: 0.198))
        if king {
            let gold = UIColor(red: 0.95, green: 0.59, blue: 0.08, alpha: 1)
            root.addChildNode(shape(SCNTorus(ringRadius: 0.14, pipeRadius: 0.032), color: gold, metalness: 0.75, roughness: 0.18, y: 0.245))
            for index in 0..<5 {
                let angle = Float(index) * .pi * 2 / 5
                let jewel = shape(SCNCone(topRadius: 0, bottomRadius: 0.035, height: 0.10), color: gold, metalness: 0.7, roughness: 0.16, y: 0.31)
                jewel.position.x = cos(angle) * 0.115
                jewel.position.z = sin(angle) * 0.115
                root.addChildNode(jewel)
            }
        }
        scene.rootNode.addChildNode(root)
    }

    private static func addChessPiece(_ kind: String, white: Bool, at position: SCNVector3, to scene: SCNScene) {
        let root = SCNNode()
        root.position = position
        let body = white ? UIColor(red: 0.91, green: 0.88, blue: 0.77, alpha: 1) : UIColor(red: 0.025, green: 0.034, blue: 0.052, alpha: 1)
        let accent = white ? UIColor(red: 1, green: 0.97, blue: 0.88, alpha: 1) : UIColor(red: 0.15, green: 0.18, blue: 0.24, alpha: 1)
        let metalness: CGFloat = 0
        root.addChildNode(shape(SCNCylinder(radius: 0.26, height: 0.08), color: body, metalness: metalness, roughness: 0.16, y: 0.05))
        root.addChildNode(shape(SCNTorus(ringRadius: 0.205, pipeRadius: 0.038), color: accent, metalness: metalness, roughness: 0.14, y: 0.105))
        root.addChildNode(shape(SCNCylinder(radius: 0.19, height: 0.06), color: body, metalness: metalness, roughness: 0.16, y: 0.14))

        switch kind {
        case "pawn":
            root.addChildNode(shape(turnedChessStem(radius: 0.17, height: 0.28), color: body, metalness: metalness, roughness: 0.16, y: 0.30))
            root.addChildNode(shape(SCNSphere(radius: 0.125), color: accent, metalness: metalness, roughness: 0.12, y: 0.50))
        case "rook":
            root.addChildNode(shape(SCNCylinder(radius: 0.15, height: 0.38), color: body, metalness: metalness, roughness: 0.16, y: 0.35))
            root.addChildNode(shape(SCNCylinder(radius: 0.22, height: 0.13), color: accent, metalness: metalness, roughness: 0.13, y: 0.59))
            for index in 0..<4 {
                let angle = Float(index) * .pi / 2
                let battlement = shape(SCNBox(width: 0.09, height: 0.12, length: 0.09, chamferRadius: 0.012), color: body, metalness: metalness, roughness: 0.14, y: 0.71)
                battlement.position.x = cos(angle) * 0.15
                battlement.position.z = sin(angle) * 0.15
                root.addChildNode(battlement)
            }
        case "knight":
            root.addChildNode(shape(SCNCone(topRadius: 0.10, bottomRadius: 0.18, height: 0.34), color: body, metalness: metalness, roughness: 0.16, y: 0.34))
            let head = shape(SCNCapsule(capRadius: 0.125, height: 0.40), color: accent, metalness: metalness, roughness: 0.12, y: 0.60)
            head.eulerAngles.x = -0.38
            head.position.z = -0.07
            root.addChildNode(head)
            let muzzle = shape(SCNCone(topRadius: 0.055, bottomRadius: 0.105, height: 0.22), color: body, metalness: metalness, roughness: 0.28, y: 0.75)
            muzzle.eulerAngles.x = -.pi / 2.5
            muzzle.position.z = -0.16
            root.addChildNode(muzzle)
            for side: Float in [-1, 1] {
                let eye = shape(SCNSphere(radius: 0.018), color: white ? UIColor(white: 0.07, alpha: 1) : UIColor(red: 0.65, green: 0.5, blue: 0.29, alpha: 1), roughness: 0.3, y: 0.71)
                eye.position = SCNVector3(side * 0.12, 0.71, -0.14)
                root.addChildNode(eye)
                let ear = shape(SCNCone(topRadius: 0, bottomRadius: 0.026, height: 0.12), color: body, roughness: 0.3, y: 0.82)
                ear.position.x = side * 0.055; ear.position.z = -0.015
                root.addChildNode(ear)
            }
        case "bishop":
            root.addChildNode(shape(turnedChessStem(radius: 0.18, height: 0.40), color: body, metalness: metalness, roughness: 0.16, y: 0.37))
            let head = shape(SCNSphere(radius: 0.145), color: accent, metalness: metalness, roughness: 0.12, y: 0.64)
            head.scale.y = 1.35
            root.addChildNode(head)
            root.addChildNode(shape(SCNCone(topRadius: 0, bottomRadius: 0.052, height: 0.16), color: body, metalness: metalness, roughness: 0.13, y: 0.85))
        case "queen":
            root.addChildNode(shape(turnedChessStem(radius: 0.19, height: 0.48), color: body, metalness: metalness, roughness: 0.15, y: 0.41))
            root.addChildNode(shape(SCNTorus(ringRadius: 0.145, pipeRadius: 0.035), color: accent, metalness: metalness, roughness: 0.12, y: 0.69))
            for index in 0..<6 {
                let angle = Float(index) * .pi / 3
                let pearl = shape(SCNSphere(radius: 0.045), color: body, metalness: metalness, roughness: 0.1, y: 0.80)
                pearl.position.x = cos(angle) * 0.13
                pearl.position.z = sin(angle) * 0.13
                root.addChildNode(pearl)
            }
            root.addChildNode(shape(SCNSphere(radius: 0.065), color: accent, metalness: metalness, roughness: 0.1, y: 0.86))
        default:
            root.addChildNode(shape(turnedChessStem(radius: 0.20, height: 0.50), color: body, metalness: metalness, roughness: 0.15, y: 0.42))
            root.addChildNode(shape(SCNSphere(radius: 0.12), color: accent, metalness: metalness, roughness: 0.1, y: 0.74))
            root.addChildNode(shape(SCNBox(width: 0.07, height: 0.23, length: 0.07, chamferRadius: 0.015), color: body, metalness: metalness, roughness: 0.12, y: 0.93))
            root.addChildNode(shape(SCNBox(width: 0.22, height: 0.065, length: 0.07, chamferRadius: 0.012), color: body, metalness: metalness, roughness: 0.12, y: 0.98))
        }
        // Satin resin/ebony, not chrome. Keep silhouettes legible under the key light.
        root.childNodes.forEach { $0.castsShadow = true; $0.geometry?.firstMaterial?.roughness.contents = 0.30 }
        scene.rootNode.addChildNode(root)
    }

    private static func turnedChessStem(radius: CGFloat, height: CGFloat) -> SCNGeometry {
        let profile: [(Float, Float)] = [(-1,0.98),(-0.82,0.91),(-0.66,0.70),(-0.43,0.52),(-0.15,0.40),(0.18,0.34),(0.46,0.36),(0.68,0.46),(0.82,0.61),(1,0.58)]
        var vertices: [SCNVector3] = []; var normals: [SCNVector3] = []; var indices: [Int32] = []
        let segments = 64
        for ring in profile.indices {
            let before = profile[max(0, ring - 1)]; let after = profile[min(profile.count - 1, ring + 1)]
            let slope = (after.1 - before.1) * Float(radius) / ((after.0 - before.0) * Float(height * 0.5))
            let norm = sqrt(1 + slope * slope)
            for n in 0...segments {
                let angle = Float(n) * .pi * 2 / Float(segments)
                vertices.append(SCNVector3(cos(angle) * profile[ring].1 * Float(radius), profile[ring].0 * Float(height * 0.5), sin(angle) * profile[ring].1 * Float(radius)))
                normals.append(SCNVector3(cos(angle) / norm, -slope / norm, sin(angle) / norm))
            }
        }
        for ring in 0..<(profile.count - 1) { for n in 0..<segments {
            let a = Int32(ring * (segments + 1) + n); let b = a + Int32(segments + 1)
            indices += [a, b + 1, a + 1, a, b, b + 1]
        } }
        return SCNGeometry(sources: [SCNGeometrySource(vertices: vertices), SCNGeometrySource(normals: normals)], elements: [SCNGeometryElement(indices: indices, primitiveType: .triangles)])
    }

    private static func addLudo(to scene: SCNScene, dieValue: Int) {
        addBoardBase(to: scene, width: 6.65, length: 6.65)
        let colors: [UIColor] = [.systemRed, UIColor(red: 0.08, green: 0.58, blue: 0.96, alpha: 1), .systemGreen, .systemYellow]
        let cell: Float = 0.365
        for row in 0..<15 {
            for column in 0..<15 {
                let zone: Int? = row < 6 && column < 6 ? 0 : row < 6 && column > 8 ? 1 : row > 8 && column > 8 ? 2 : row > 8 && column < 6 ? 3 : nil
                let lane: Int? = row == 7 && (1...6).contains(column) ? 0 : column == 7 && (1...6).contains(row) ? 1 : row == 7 && (8...13).contains(column) ? 2 : column == 7 && (8...13).contains(row) ? 3 : nil
                let color: UIColor
                if let lane { color = colors[lane] }
                else if row >= 6 && row <= 8 || column >= 6 && column <= 8 { color = UIColor(white: 0.94, alpha: 1) }
                else if let zone { color = colors[zone].withAlphaComponent(0.74) }
                else { color = UIColor(white: 0.91, alpha: 1) }
                let tile = SCNBox(width: CGFloat(cell * 0.96), height: 0.055, length: CGFloat(cell * 0.96), chamferRadius: 0.016)
                tile.firstMaterial = material(color: color, roughness: 0.31)
                let node = SCNNode(geometry: tile)
                node.position = ludoPosition(row: row, column: column, cell: cell, y: 0.06)
                scene.rootNode.addChildNode(node)
            }
        }

        let slots = [
            [(2, 2), (2, 4), (4, 2), (4, 4)],
            [(2, 10), (2, 12), (4, 10), (4, 12)],
            [(10, 10), (10, 12), (12, 10), (12, 12)],
            [(10, 2), (10, 4), (12, 2), (12, 4)],
        ]
        for player in 0..<4 {
            for slot in slots[player] {
                let p = ludoPosition(row: slot.0, column: slot.1, cell: cell, y: 0.11)
                let ring = SCNTorus(ringRadius: 0.14, pipeRadius: 0.025)
                ring.firstMaterial = material(color: colors[player], metalness: 0.18, roughness: 0.18)
                let ringNode = SCNNode(geometry: ring)
                ringNode.position = p
                scene.rootNode.addChildNode(ringNode)
                addLudoPawn(color: colors[player], at: SCNVector3(p.x, 0.16, p.z), to: scene, active: player == 0 && slot == slots[0][0])
            }
        }

        let die = makeDie(value: dieValue)
        die.name = "wapi.game.die"
        die.position = SCNVector3(0, 0.68, 0)
        die.eulerAngles = dieRotation(for: dieValue)
        die.castsShadow = true
        scene.rootNode.addChildNode(die)
    }

    private static func addLudoPawn(color: UIColor, at position: SCNVector3, to scene: SCNScene, active: Bool) {
        let root = SCNNode()
        root.position = position
        root.addChildNode(shape(SCNCylinder(radius: 0.15, height: 0.075), color: color, metalness: 0.18, roughness: 0.14, y: 0.04))
        root.addChildNode(shape(SCNTorus(ringRadius: 0.115, pipeRadius: 0.026), color: color, metalness: 0.25, roughness: 0.12, y: 0.09))
        root.addChildNode(shape(SCNCone(topRadius: 0.055, bottomRadius: 0.115, height: 0.25), color: color, metalness: 0.20, roughness: 0.14, y: 0.235))
        root.addChildNode(shape(SCNSphere(radius: 0.095), color: color, metalness: 0.20, roughness: 0.1, y: 0.40))
        root.childNodes.forEach { $0.castsShadow = true }
        if active {
            root.runAction(.repeatForever(.sequence([
                .moveBy(x: 0, y: 0.055, z: 0, duration: 0.48),
                .moveBy(x: 0, y: -0.055, z: 0, duration: 0.48),
            ])))
        }
        scene.rootNode.addChildNode(root)
    }

    private static let poolPocketLocations: [(Float, Float)] = [(-3.18, -1.75), (0, -1.83), (3.18, -1.75), (-3.18, 1.75), (0, 1.83), (3.18, 1.75)]
    private static let poolPocketRadius: CGFloat = 0.305

    private static func poolSlab(width: CGFloat, length: CGFloat, depth: CGFloat) -> SCNShape {
        let outline = UIBezierPath(roundedRect: CGRect(x: -width / 2, y: -length / 2, width: width, height: length), cornerRadius: 0.08)
        // UIKit's default flatness is large relative to metre-sized geometry;
        // without this, the small circular pockets tessellate into diamonds.
        outline.flatness = 0.005
        outline.usesEvenOddFillRule = true
        for (x, z) in poolPocketLocations {
            // Cut the bed to the OUTSIDE of the leather, not its aperture.
            // Otherwise its extruded wooden walls coincide with the liner and
            // flicker through the black throat as brown bands (z-fighting).
            let cutout = poolPocketRadius + 0.026
            let hole = UIBezierPath(ovalIn: CGRect(x: CGFloat(x) - cutout, y: CGFloat(z) - cutout, width: cutout * 2, height: cutout * 2))
            hole.flatness = 0.005
            outline.append(hole.reversing())
        }
        let geometry = SCNShape(path: outline, extrusionDepth: depth)
        geometry.chamferRadius = 0
        return geometry
    }

    /// Fit once per viewport change; never pan or zoom in response to a shot.
    static func framePoolCamera(in scene: SCNScene, viewport: CGSize) {
        guard let camera = scene.rootNode.childNode(withName: "wapi.game.camera", recursively: true) else { return }
        let aspect = Float(viewport.width / max(viewport.height, 1))
        let pitch: Float = 68 * .pi / 180
        let tangent: Float = tan(33 * .pi / 360)
        var distance: Float = 0
        for x: Float in [-3.8, 3.8] { for z: Float in [-2.35, 2.35] { for y: Float in [-0.55, 0.5] {
            let depth = y * sin(pitch) + z * cos(pitch)
            let vertical = y * cos(pitch) - z * sin(pitch)
            distance = max(distance, max(abs(x) / (tangent * max(aspect, 0.4)), abs(vertical) / tangent) + depth)
        } } }
        distance *= 1.04
        camera.camera?.fieldOfView = 33
        camera.camera?.projectionDirection = .vertical
        camera.position = SCNVector3(0, sin(pitch) * distance, cos(pitch) * distance)
        camera.constraints = [lookAt(SCNVector3Zero)]
    }

    private static func addPool(to scene: SCNScene) {
        let contactDelegate = PoolPhysicsDelegate()
        scene.physicsWorld.contactDelegate = contactDelegate
        contactDelegate.poolScene = scene as? PoolScene
        (scene as? PoolScene)?.retainedContactDelegate = contactDelegate
        let wood = textureMaterial(named: "WapiWalnutTexture", fallback: UIColor(red: 0.32, green: 0.105, blue: 0.052, alpha: 1), roughness: 0.40)
        wood.multiply.contents = UIColor(red: 0.78, green: 0.47, blue: 0.32, alpha: 1)
        let felt = bluePoolClothMaterial()
        let base = poolSlab(width: 7.2, length: 4.35, depth: 0.48)
        base.firstMaterial = wood
        let baseNode = SCNNode(geometry: base)
        baseNode.name = "wapi.pool.wood"
        baseNode.eulerAngles.x = -.pi / 2
        baseNode.position.y = -0.40
        baseNode.castsShadow = true
        scene.rootNode.addChildNode(baseNode)

        let cloth = poolSlab(width: 7.15, length: 4.30, depth: 0.11)
        cloth.firstMaterial = felt
        let clothNode = SCNNode(geometry: cloth)
        clothNode.name = "wapi.pool.cloth"
        clothNode.eulerAngles.x = -.pi / 2
        clothNode.position.y = 0.105
        scene.rootNode.addChildNode(clothNode)

        // Subtle competition-table underglow: it gives the table a physical
        // silhouette in the dark room without colouring the playing surface.
        let glowMaterial = material(color: UIColor(red: 0.01, green: 0.24, blue: 0.95, alpha: 1), metalness: 0.1, roughness: 0.18)
        glowMaterial.emission.contents = UIColor(red: 0.01, green: 0.30, blue: 1.0, alpha: 1)
        let glowStrips: [(CGFloat, CGFloat, Float, Float)] = [(6.55, 0.035, 0, -2.17), (6.55, 0.035, 0, 2.17), (0.035, 3.72, -3.58, 0), (0.035, 3.72, 3.58, 0)]
        for (width, length, x, z) in glowStrips {
            let strip = SCNNode(geometry: SCNBox(width: width, height: 0.028, length: length, chamferRadius: 0.012))
            strip.geometry?.firstMaterial = glowMaterial
            strip.position = SCNVector3(Float(x), -0.39, Float(z))
            scene.rootNode.addChildNode(strip)
        }
        addPoolReturnTrack(to: scene)

        for z in [-2.02, 2.02] as [Float] {
          for x in [-1.73, 1.73] as [Float] {
            let rail = SCNBox(width: 2.78, height: 0.30, length: 0.34, chamferRadius: 0.06)
            rail.firstMaterial = wood
            let node = SCNNode(geometry: rail)
            node.position = SCNVector3(x, 0.33, z)
            node.castsShadow = true
            scene.rootNode.addChildNode(node)
            let cushion = SCNBox(width: 2.70, height: 0.19, length: 0.16, chamferRadius: 0.05)
            let visibleCushion = poolCushionGeometry(width: 2.70, height: 0.19, depth: 0.16)
            visibleCushion.firstMaterial = material(color: UIColor(red: 0.018, green: 0.26, blue: 0.34, alpha: 1), roughness: 0.9)
            let cushionNode = SCNNode(geometry: visibleCushion)
            cushionNode.eulerAngles.y = z < 0 ? 0 : .pi
            cushionNode.position = SCNVector3(x, 0.31, z > 0 ? z - 0.23 : z + 0.23)
            cushionNode.physicsBody = SCNPhysicsBody(type: .static, shape: SCNPhysicsShape(geometry: cushion, options: nil))
            cushionNode.physicsBody?.restitution = 0.82
            cushionNode.physicsBody?.categoryBitMask = PoolPhysicsCategory.rail
            cushionNode.physicsBody?.collisionBitMask = PoolPhysicsCategory.ball
            cushionNode.physicsBody?.contactTestBitMask = PoolPhysicsCategory.ball
            scene.rootNode.addChildNode(cushionNode)
          }
        }
        for x in [-3.45, 3.45] as [Float] {
            let rail = SCNBox(width: 0.34, height: 0.30, length: 2.75, chamferRadius: 0.06)
            rail.firstMaterial = wood
            let node = SCNNode(geometry: rail)
            node.position = SCNVector3(x, 0.33, 0)
            node.castsShadow = true
            scene.rootNode.addChildNode(node)
            let cushion = SCNBox(width: 0.16, height: 0.19, length: 2.67, chamferRadius: 0.05)
            let visibleCushion = poolCushionGeometry(width: 2.67, height: 0.19, depth: 0.16)
            visibleCushion.firstMaterial = material(color: UIColor(red: 0.018, green: 0.26, blue: 0.34, alpha: 1), roughness: 0.9)
            let cushionNode = SCNNode(geometry: visibleCushion)
            // Rotate the visual child only: the existing axis-aligned physical
            // cushion keeps exactly the same collisions and restitution.
            let visual = SCNNode(geometry: visibleCushion)
            visual.eulerAngles.y = x < 0 ? .pi / 2 : -.pi / 2
            cushionNode.geometry = nil; cushionNode.addChildNode(visual)
            cushionNode.position = SCNVector3(x > 0 ? x - 0.23 : x + 0.23, 0.31, 0)
            cushionNode.physicsBody = SCNPhysicsBody(type: .static, shape: SCNPhysicsShape(geometry: cushion, options: nil))
            cushionNode.physicsBody?.restitution = 0.82
            cushionNode.physicsBody?.categoryBitMask = PoolPhysicsCategory.rail
            cushionNode.physicsBody?.collisionBitMask = PoolPhysicsCategory.ball
            cushionNode.physicsBody?.contactTestBitMask = PoolPhysicsCategory.ball
            scene.rootNode.addChildNode(cushionNode)
        }

        for (x, z) in poolPocketLocations {
            let radius = poolPocketRadius
            // The aperture is flush with the cloth. No reflective metal tube:
            // a thin dark leather lip surrounds a black, recessed throat.
            let rim = SCNTorus(ringRadius: radius + 0.013, pipeRadius: 0.013)
            rim.ringSegmentCount = 64; rim.pipeSegmentCount = 12
            let leather = SCNMaterial(); leather.lightingModel = .constant
            leather.diffuse.contents = UIColor(red: 0.055, green: 0.021, blue: 0.012, alpha: 1)
            rim.firstMaterial = leather
            let rimNode = SCNNode(geometry: rim)
            rimNode.position = SCNVector3(x, 0.215, z)
            rimNode.name = "wapi.pool.leatherLip"
            scene.rootNode.addChildNode(rimNode)
            let liner = SCNTube(innerRadius: radius, outerRadius: radius + 0.028, height: 0.70)
            liner.radialSegmentCount = 64
            let throat = SCNMaterial(); throat.lightingModel = .constant
            throat.diffuse.contents = UIColor(red: 0.012, green: 0.006, blue: 0.004, alpha: 1)
            liner.firstMaterial = throat
            let linerNode = SCNNode(geometry: liner)
            linerNode.position = SCNVector3(x, -0.135, z)
            scene.rootNode.addChildNode(linerNode)
            let pocket = SCNCylinder(radius: radius, height: 0.015)
            let darkness = SCNMaterial()
            darkness.lightingModel = .constant
            darkness.diffuse.contents = UIColor(white: 0.003, alpha: 1)
            pocket.firstMaterial = darkness
            let pocketNode = SCNNode(geometry: pocket)
            pocketNode.position = SCNVector3(x, -0.465, z)
            pocketNode.name = "wapi.pool.pocket"
            // Sensor stays at cloth height; the visible bottom is recessed.
            let sensor = SCNNode()
            sensor.position = SCNVector3(x, 0.30, z)
            sensor.name = "wapi.pool.pocket"
            sensor.physicsBody = SCNPhysicsBody(type: .static, shape: SCNPhysicsShape(geometry: SCNCylinder(radius: radius * 0.72, height: 0.28), options: nil))
            sensor.physicsBody?.categoryBitMask = PoolPhysicsCategory.pocket
            sensor.physicsBody?.collisionBitMask = 0
            sensor.physicsBody?.contactTestBitMask = PoolPhysicsCategory.ball
            scene.rootNode.addChildNode(sensor)
            scene.rootNode.addChildNode(pocketNode)
        }

        let sightMaterial = material(color: UIColor(red: 0.88, green: 0.94, blue: 1, alpha: 1), metalness: 0.45, roughness: 0.14)
        for x in [-2.55, -1.70, -0.85, 0.85, 1.70, 2.55] as [Float] {
            for z in [-2.04, 2.04] as [Float] {
                let sight = SCNNode(geometry: SCNSphere(radius: 0.035))
                sight.geometry?.firstMaterial = sightMaterial
                sight.position = SCNVector3(x, 0.50, z)
                scene.rootNode.addChildNode(sight)
            }
        }
        for z in [-1.05, 0, 1.05] as [Float] {
            for x in [-3.46, 3.46] as [Float] {
                let sight = SCNNode(geometry: SCNSphere(radius: 0.035))
                sight.geometry?.firstMaterial = sightMaterial
                sight.position = SCNVector3(x, 0.50, z)
                scene.rootNode.addChildNode(sight)
            }
        }

        let poolLight = light(type: .omni, intensity: 250, color: UIColor(red: 0.96, green: 0.98, blue: 1, alpha: 1), position: SCNVector3(0, 5.8, 0))
        poolLight.name = "wapi.pool.dynamicLight"
        scene.rootNode.addChildNode(poolLight)

        var ballID = 1
        for row in 0..<5 {
            for index in 0...row {
                let id = ballID
                let x = Float(row) * 0.255 + 0.65
                let z = (Float(index) - Float(row) / 2) * 0.295
                addPoolBall(id: id, color: poolBallColor(id: id), striped: id > 8, at: SCNVector3(x, 0.40, z), to: scene)
                ballID += 1
            }
        }
        addPoolBall(id: 0, color: .white, striped: false, at: SCNVector3(-1.65, 0.40, 0), to: scene)

        let shaft = SCNCone(topRadius: 0.026, bottomRadius: 0.050, height: 3.7)
        shaft.radialSegmentCount = 48
        shaft.firstMaterial = material(color: UIColor(red: 0.91, green: 0.77, blue: 0.51, alpha: 1), roughness: 0.32)
        let cue = SCNNode()
        cue.name = "wapi.game.cue"
        let shaftNode = SCNNode(geometry: shaft)
        shaftNode.name = "wapi.pool.cue.shaft"
        shaftNode.castsShadow = true
        cue.addChildNode(shaftNode)
        let butt = SCNNode(geometry: SCNCylinder(radius: 0.057, height: 0.72))
        butt.name = "wapi.pool.cue.butt"
        butt.geometry?.firstMaterial = material(color: UIColor(red: 0.12, green: 0.035, blue: 0.012, alpha: 1), roughness: 0.18)
        butt.position.y = -1.48
        cue.addChildNode(butt)
        for y in [-1.82, -1.12] as [Float] {
            let collar = SCNNode(geometry: SCNCylinder(radius: 0.058, height: 0.025))
            collar.geometry?.firstMaterial = material(color: UIColor(red: 0.78, green: 0.69, blue: 0.48, alpha: 1), metalness: 0.65, roughness: 0.25)
            collar.position.y = y
            cue.addChildNode(collar)
        }
        let ferrule = SCNNode(geometry: SCNCylinder(radius: 0.032, height: 0.13))
        ferrule.geometry?.firstMaterial = material(color: UIColor(red: 0.96, green: 0.91, blue: 0.76, alpha: 1), roughness: 0.12)
        ferrule.position.y = 1.89
        cue.addChildNode(ferrule)
        let tip = SCNNode(geometry: SCNCylinder(radius: 0.034, height: 0.055))
        tip.geometry?.firstMaterial = material(color: UIColor(red: 0.02, green: 0.20, blue: 0.39, alpha: 1), roughness: 0.26)
        tip.position.y = 1.98
        cue.addChildNode(tip)
        cue.position = SCNVector3(-3.0, 0.52, 0)
        cue.eulerAngles = SCNVector3(0, 0, -Float.pi / 2)
        cue.castsShadow = true
        scene.rootNode.addChildNode(cue)
    }

    /** Compact three-lane U return behind the head rail. Potted balls travel
     * into its numbered slots rather than disappearing or filling a long,
     * implausible straight decorative bar. */
    private static func addPoolReturnTrack(to scene: SCNScene) {
        let track = SCNNode()
        track.name = "wapi.pool.returnTrack"
        let bed = SCNBox(width: 4.62, height: 0.14, length: 0.78, chamferRadius: 0.07)
        bed.firstMaterial = material(color: UIColor(red: 0.018, green: 0.035, blue: 0.065, alpha: 1), metalness: 0.38, roughness: 0.22)
        let bedNode = SCNNode(geometry: bed); bedNode.position = SCNVector3(0, 0.25, -2.76); track.addChildNode(bedNode)
        let metal = material(color: UIColor(red: 0.54, green: 0.62, blue: 0.70, alpha: 1), metalness: 0.82, roughness: 0.20)
        for z in [-3.02, -2.77, -2.52] as [Float] {
            let guide = SCNBox(width: 4.18, height: 0.065, length: 0.065, chamferRadius: 0.032)
            guide.firstMaterial = metal
            let guideNode = SCNNode(geometry: guide); guideNode.position = SCNVector3(0, 0.42, z); track.addChildNode(guideNode)
        }
        for x in [-2.07, 2.07] as [Float] {
            let end = SCNBox(width: 0.065, height: 0.065, length: 0.55, chamferRadius: 0.032)
            end.firstMaterial = metal
            let endNode = SCNNode(geometry: end); endNode.position = SCNVector3(x, 0.42, -2.76); track.addChildNode(endNode)
        }
        scene.rootNode.addChildNode(track)
    }

    private static func poolBallColor(id: Int) -> UIColor {
        let colors: [UIColor] = [.systemYellow, .systemBlue, .systemRed, .systemPurple, .systemOrange, .systemGreen, UIColor(red: 0.52, green: 0.02, blue: 0.04, alpha: 1), .black]
        return id == 0 ? .white : colors[(id - 1) % colors.count]
    }

    private static func makePoolReturnBall(id: Int) -> SCNNode {
        let root = SCNNode()
        root.name = "wapi.pool.returnBall.\(id)"
        let sphere = SCNSphere(radius: 0.112)
        sphere.segmentCount = 40
        let resin = material(color: poolBallColor(id: id), metalness: 0, roughness: 0.22)
        resin.diffuse.contents = rgbaTexture(poolBallTexture(id: id, color: poolBallColor(id: id), striped: id > 8))
        sphere.firstMaterial = resin
        root.addChildNode(SCNNode(geometry: sphere))
        root.castsShadow = true
        return root
    }

    static func applyPoolAppearance(in scene: SCNScene?, tableTheme: String, cueStyle: String) {
        guard let root = scene?.rootNode else { return }
        let feltColor: UIColor = switch tableTheme {
        case "navy": UIColor(red: 0.018, green: 0.16, blue: 0.44, alpha: 1)
        case "emerald": UIColor(red: 0.015, green: 0.36, blue: 0.25, alpha: 1)
        default: UIColor(red: 0.015, green: 0.45, blue: 0.73, alpha: 1)
        }
        root.childNode(withName: "wapi.pool.cloth", recursively: true)?.geometry?.firstMaterial?.diffuse.contents = feltColor
        let shaft: UIColor
        let grip: UIColor
        switch cueStyle {
        case "walnut": shaft = UIColor(red: 0.36, green: 0.13, blue: 0.04, alpha: 1); grip = UIColor(red: 0.10, green: 0.022, blue: 0.008, alpha: 1)
        case "carbon": shaft = UIColor(red: 0.09, green: 0.12, blue: 0.15, alpha: 1); grip = UIColor(red: 0.015, green: 0.02, blue: 0.027, alpha: 1)
        default: shaft = UIColor(red: 0.93, green: 0.76, blue: 0.43, alpha: 1); grip = UIColor(red: 0.12, green: 0.035, blue: 0.012, alpha: 1)
        }
        root.childNode(withName: "wapi.pool.cue.shaft", recursively: true)?.geometry?.firstMaterial?.diffuse.contents = shaft
        root.childNode(withName: "wapi.pool.cue.butt", recursively: true)?.geometry?.firstMaterial?.diffuse.contents = grip
    }

    /// Practice opponent aims at a real ball/pocket pair after the table settles.
    static func nextPoolPracticeShot(in scene: SCNScene, group: Int = 0) -> (Float, Int) {
        guard let cue = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: false) else { return (0, 0) }
        guard scene.rootNode.childNodes.contains(where: { $0.name?.hasPrefix("wapi.pool.ball.") == true && $0 !== cue }) else { return (0, 0) }
        let start = cue.presentation.position
        let remaining = poolRemaining(in: scene)
        let targets = WapiPoolRules.targets(group: group, remaining: remaining)
        var best: (Float, Int) = (0, 45); var bestDistance: Float = .greatestFiniteMagnitude
        for node in scene.rootNode.childNodes where node.name?.hasPrefix("wapi.pool.ball.") == true && node !== cue {
            guard let id = node.name?.split(separator: ".").last.flatMap({ Int($0) }), targets.contains(id) else { continue }
            let target = node.presentation.position
            if bestDistance == .greatestFiniteMagnitude { best = (atan2(target.z - start.z, target.x - start.x), 40) }
            for pocket in poolPocketLocations {
                let dx = pocket.0 - target.x; let dz = pocket.1 - target.z
                let distance = max(0.001, sqrt(dx * dx + dz * dz))
                let ghost = SCNVector3(target.x - dx / distance * 0.29, target.y, target.z - dz / distance * 0.29)
                let gx = ghost.x - start.x; let gz = ghost.z - start.z
                let travel = sqrt(gx * gx + gz * gz)
                let alignment = (gx * dx + gz * dz) / max(0.001, travel * distance)
                if alignment > 0.35 && travel + distance < bestDistance {
                    bestDistance = travel + distance
                    best = (atan2(gz, gx), min(82, max(32, Int(30 + travel * 8 + distance * 5))))
                }
            }
        }
        return best
    }

    static func poolRemaining(in scene: SCNScene) -> Set<Int> {
        let removed = (scene as? PoolScene)?.retainedContactDelegate?.pocketedIDs ?? []
        return Set(scene.rootNode.childNodes.compactMap { node -> Int? in
            guard node.name?.hasPrefix("wapi.pool.ball.") == true,
                  let id = node.name?.split(separator: ".").last.flatMap({ Int($0) }), id > 0, !removed.contains(id) else { return nil }
            return id
        })
    }

    static func poolOutcome(in scene: SCNScene) -> WapiPoolShotOutcome {
        let events = (scene as? PoolScene)?.retainedContactDelegate
        return WapiPoolShotOutcome(remaining: poolRemaining(in: scene), pocketed: events?.pocketedIDs ?? [],
                                  firstContact: events?.firstContact, scratched: events?.scratched ?? false,
                                  aiShots: Dictionary(uniqueKeysWithValues: (0...2).map { ($0, nextPoolPracticeShot(in: scene, group: $0)) }))
    }

    @discardableResult static func placePoolCue(in scene: SCNScene, x: Float, z: Float) -> Bool {
        guard x.isFinite, z.isFinite, (-2.94 ... -0.45).contains(x), (-1.46 ... 1.46).contains(z),
              let white = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: false) else { return false }
        for pocket in poolPocketLocations {
            if hypot(x - pocket.0, z - pocket.1) < 0.42 { return false }
        }
        for ball in scene.rootNode.childNodes where ball.name?.hasPrefix("wapi.pool.ball.") == true && ball !== white {
            if hypot(x - ball.position.x, z - ball.position.z) < 0.302 { return false }
        }
        white.physicsBody?.clearAllForces(); white.physicsBody?.velocity = SCNVector3Zero
        white.position = SCNVector3(x, 0.40, z); white.physicsBody?.resetTransform()
        setPoolPlacementHand(in: scene, visible: true)
        return true
    }

    static func setPoolPlacementHand(in scene: SCNScene, visible: Bool) {
        let key = "wapi.pool.hand"
        guard visible, let white = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: false) else {
            scene.rootNode.childNode(withName: key, recursively: false)?.isHidden = true
            return
        }
        let hand: SCNNode
        if let existing = scene.rootNode.childNode(withName: key, recursively: false) { hand = existing }
        else {
            hand = SCNNode(); hand.name = key
            let glove = material(color: UIColor(red: 0.93, green: 0.935, blue: 0.91, alpha: 1), roughness: 0.94)
            let seam = material(color: UIColor(red: 0.56, green: 0.62, blue: 0.66, alpha: 1), roughness: 0.96)
            let cuff = material(color: UIColor(red: 0.016, green: 0.07, blue: 0.13, alpha: 1), roughness: 0.96)
            func part(_ position: SCNVector3, _ scale: SCNVector3, fabric: SCNMaterial? = nil, rotation: SCNVector3 = SCNVector3Zero) {
                let geometry = SCNSphere(radius: 1); geometry.segmentCount = 32
                let node = SCNNode(geometry: geometry); geometry.firstMaterial = fabric ?? glove
                node.eulerAngles = rotation
                node.position = position; node.scale = scale; node.castsShadow = true; hand.addChildNode(node)
            }
            part(SCNVector3(0.07, 0.25, 0.20), SCNVector3(0.15, 0.060, 0.165))
            for finger in 0..<4 {
                let fx = -0.045 + Float(finger) * 0.06
                let reach: Float = finger == 3 ? 0.026 : 0
                part(SCNVector3(fx, 0.22, 0.060 + reach), SCNVector3(0.029, 0.047, 0.105), rotation: SCNVector3(-0.38, 0, 0))
                part(SCNVector3(fx, 0.13, -0.005 + reach), SCNVector3(0.029, 0.075, 0.035), rotation: SCNVector3(-0.31, 0, 0))
                part(SCNVector3(fx, 0.270, 0.106), SCNVector3(0.027, 0.014, 0.031))
            }
            part(SCNVector3(-0.090, 0.18, 0.148), SCNVector3(0.045, 0.049, 0.098), rotation: SCNVector3(0, -0.66, 0))
            part(SCNVector3(-0.128, 0.11, 0.080), SCNVector3(0.038, 0.06, 0.041))
            for stitch in -1...1 { part(SCNVector3(0.07 + Float(stitch) * 0.049, 0.310, 0.207), SCNVector3(0.003, 0.003, 0.066), fabric: seam) }
            part(SCNVector3(0.07, 0.25, 0.361), SCNVector3(0.112, 0.057, 0.068))
            part(SCNVector3(0.07, 0.25, 0.403), SCNVector3(0.107, 0.054, 0.059), fabric: cuff)
            for rib in -3...3 { part(SCNVector3(0.07 + Float(rib) * 0.024, 0.299, 0.403), SCNVector3(0.002, 0.003, 0.035), fabric: seam) }
            scene.rootNode.addChildNode(hand)
        }
        hand.position = SCNVector3(white.position.x + 0.18, white.position.y, white.position.z)
        hand.isHidden = false
        scene.rootNode.childNode(withName: "wapi.game.cue", recursively: true)?.isHidden = true
        scene.rootNode.childNode(withName: "wapi.pool.guide", recursively: false)?.isHidden = true
    }

    private static func addPoolBall(id: Int, color: UIColor, striped: Bool, at position: SCNVector3, to scene: SCNScene) {
        let root = SCNNode()
        root.name = "wapi.pool.ball.\(id)"
        root.position = position
        let ball = SCNSphere(radius: 0.145)
        ball.segmentCount = 48
        let resin = material(color: color, metalness: 0, roughness: 0.28)
        resin.diffuse.contents = rgbaTexture(poolBallTexture(id: id, color: color, striped: striped))
        ball.firstMaterial = resin
        root.addChildNode(SCNNode(geometry: ball))
        // Stripe and printed numbers share the sphere: no raised torus/badge.
        root.eulerAngles.x = -.pi / 3
        root.castsShadow = true
        root.physicsBody = SCNPhysicsBody(type: .dynamic, shape: SCNPhysicsShape(geometry: ball, options: nil))
        root.physicsBody?.isAffectedByGravity = false
        root.physicsBody?.mass = 0.17
        root.physicsBody?.friction = 0.19
        root.physicsBody?.rollingFriction = 0.12
        root.physicsBody?.restitution = 0.94
        root.physicsBody?.damping = 0.34
        root.physicsBody?.angularDamping = 0.22
        root.physicsBody?.continuousCollisionDetectionThreshold = 0.05
        root.physicsBody?.categoryBitMask = PoolPhysicsCategory.ball
        root.physicsBody?.collisionBitMask = PoolPhysicsCategory.ball | PoolPhysicsCategory.rail
        root.physicsBody?.contactTestBitMask = PoolPhysicsCategory.ball | PoolPhysicsCategory.rail | PoolPhysicsCategory.pocket
        scene.rootNode.addChildNode(root)
    }

    private static func poolBallTexture(id: Int, color: UIColor, striped: Bool) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: 512, height: 256), format: format).image { context in
            (striped ? UIColor.white : color).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 512, height: 256))
            if striped {
                color.setFill()
                context.fill(CGRect(x: 0, y: 82, width: 512, height: 92))
            }
            guard id > 0 else { return }
            for x: CGFloat in [128, 384] {
                UIColor.white.setFill()
                UIBezierPath(ovalIn: CGRect(x: x - 29, y: 99, width: 58, height: 58)).fill()
                let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 36, weight: .bold), .foregroundColor: UIColor.black]
                let number = "\(id)" as NSString
                let size = number.size(withAttributes: attributes)
                number.draw(at: CGPoint(x: x - size.width / 2, y: 128 - size.height / 2), withAttributes: attributes)
            }
        }
    }

    private static func addCards(to scene: SCNScene, poker: Bool) {
        let table = SCNCylinder(radius: 3.15, height: 0.34)
        table.radialSegmentCount = 64
        table.firstMaterial = textureMaterial(named: "WapiFeltTexture", fallback: UIColor(red: 0.025, green: 0.24, blue: 0.18, alpha: 1), roughness: 0.74)
        let tableNode = SCNNode(geometry: table)
        tableNode.position.y = -0.10
        tableNode.scale.z = 0.70
        tableNode.castsShadow = true
        scene.rootNode.addChildNode(tableNode)
        let rail = SCNTorus(ringRadius: 2.75, pipeRadius: 0.28)
        rail.firstMaterial = textureMaterial(named: "WapiWalnutTexture", fallback: .brown, roughness: 0.28)
        let railNode = SCNNode(geometry: rail)
        railNode.position.y = 0.18
        railNode.scale.z = 0.70
        scene.rootNode.addChildNode(railNode)

        let suits = ["A♠", "K♥", "Q♣", "J♦", "10♠"]
        for index in 0..<5 {
            let card = SCNBox(width: 0.72, height: 0.035, length: 1.02, chamferRadius: 0.055)
            card.firstMaterial = material(color: .white, roughness: 0.45)
            let cardNode = SCNNode(geometry: card)
            cardNode.position = SCNVector3(Float(index - 2) * 0.62, 0.30 + Float(abs(index - 2)) * 0.012, 0)
            cardNode.eulerAngles.y = Float(index - 2) * 0.10
            scene.rootNode.addChildNode(cardNode)
            let text = SCNText(string: suits[index], extrusionDepth: 0.01)
            text.font = UIFont.systemFont(ofSize: 0.34, weight: .black)
            text.flatness = 0.08
            text.firstMaterial = material(color: suits[index].contains("♥") || suits[index].contains("♦") ? .systemRed : .black, roughness: 0.42)
            let textNode = SCNNode(geometry: text)
            textNode.scale = SCNVector3(0.42, 0.42, 0.42)
            textNode.position = SCNVector3(cardNode.position.x - 0.24, cardNode.position.y + 0.025, -0.22)
            textNode.eulerAngles = SCNVector3(-.pi / 2, cardNode.eulerAngles.y, 0)
            scene.rootNode.addChildNode(textNode)
        }
        if poker {
            let chipColors: [UIColor] = [.systemRed, .systemBlue, .black, .white]
            for stack in 0..<4 {
                for level in 0..<5 {
                    let chip = SCNCylinder(radius: 0.20, height: 0.055)
                    chip.firstMaterial = material(color: chipColors[stack], roughness: 0.20)
                    let node = SCNNode(geometry: chip)
                    node.position = SCNVector3(Float(stack - 2) * 0.52 + 0.25, 0.28 + Float(level) * 0.058, 1.02)
                    node.eulerAngles.y = Float(level) * 0.18
                    node.castsShadow = true
                    scene.rootNode.addChildNode(node)
                }
            }
        }
    }

    private static func addBoardBase(to scene: SCNScene, width: CGFloat, length: CGFloat) {
        let shadow = SCNBox(width: width + 0.75, height: 0.08, length: length + 0.75, chamferRadius: 0.28)
        shadow.firstMaterial = material(color: UIColor.black.withAlphaComponent(0.55), roughness: 0.95)
        let shadowNode = SCNNode(geometry: shadow)
        shadowNode.position.y = -0.49
        scene.rootNode.addChildNode(shadowNode)
        let board = SCNBox(width: width, height: 0.40, length: length, chamferRadius: 0.22)
        board.firstMaterial = textureMaterial(named: "WapiWalnutTexture", fallback: UIColor(red: 0.29, green: 0.12, blue: 0.04, alpha: 1), roughness: 0.25)
        let boardNode = SCNNode(geometry: board)
        boardNode.position.y = -0.19
        boardNode.castsShadow = true
        scene.rootNode.addChildNode(boardNode)
    }

    private static func makeDie(value: Int) -> SCNNode {
        let root = SCNNode()
        let box = SCNBox(width: 0.70, height: 0.70, length: 0.70, chamferRadius: 0.105)
        box.chamferSegmentCount = 8
        box.firstMaterial = material(color: UIColor(white: 0.985, alpha: 1), roughness: 0.15)
        root.addChildNode(SCNNode(geometry: box))
        let layout: [Int: [(Float, Float)]] = [
            1: [(0, 0)], 2: [(-0.15, -0.15), (0.15, 0.15)],
            3: [(-0.15, -0.15), (0, 0), (0.15, 0.15)],
            4: [(-0.15, -0.15), (0.15, -0.15), (-0.15, 0.15), (0.15, 0.15)],
            5: [(-0.15, -0.15), (0.15, -0.15), (0, 0), (-0.15, 0.15), (0.15, 0.15)],
            6: [(-0.15, -0.17), (0.15, -0.17), (-0.15, 0), (0.15, 0), (-0.15, 0.17), (0.15, 0.17)],
        ]
        func pip(at position: SCNVector3, scale: SCNVector3) {
            let geometry = SCNSphere(radius: 0.052)
            geometry.segmentCount = 20
            geometry.firstMaterial = material(color: UIColor(red: 0.02, green: 0.025, blue: 0.04, alpha: 1), roughness: 0.26)
            let node = SCNNode(geometry: geometry)
            node.position = position
            node.scale = scale
            root.addChildNode(node)
        }
        for point in layout[1] ?? [] { pip(at: SCNVector3(point.0, 0.354, point.1), scale: SCNVector3(1, 0.28, 1)) }
        for point in layout[6] ?? [] { pip(at: SCNVector3(point.0, -0.354, point.1), scale: SCNVector3(1, 0.28, 1)) }
        for point in layout[3] ?? [] { pip(at: SCNVector3(point.0, point.1, 0.354), scale: SCNVector3(1, 1, 0.28)) }
        for point in layout[4] ?? [] { pip(at: SCNVector3(-point.0, point.1, -0.354), scale: SCNVector3(1, 1, 0.28)) }
        for point in layout[2] ?? [] { pip(at: SCNVector3(0.354, point.0, point.1), scale: SCNVector3(0.28, 1, 1)) }
        for point in layout[5] ?? [] { pip(at: SCNVector3(-0.354, point.0, -point.1), scale: SCNVector3(0.28, 1, 1)) }
        root.eulerAngles = dieRotation(for: value)
        return root
    }

    private static func dieRotation(for value: Int) -> SCNVector3 {
        switch value {
        case 2: return SCNVector3(0, 0, -Float.pi / 2)
        case 3: return SCNVector3(Float.pi / 2, 0, 0)
        case 4: return SCNVector3(-Float.pi / 2, 0, 0)
        case 5: return SCNVector3(0, 0, Float.pi / 2)
        case 6: return SCNVector3(Float.pi, 0, 0)
        default: return SCNVector3Zero
        }
    }

    private static func boardPosition(row: Int, column: Int, cell: Float, y: Float) -> SCNVector3 {
        SCNVector3((Float(column) - 3.5) * cell, y, (Float(row) - 3.5) * cell)
    }

    private static func ludoPosition(row: Int, column: Int, cell: Float, y: Float) -> SCNVector3 {
        SCNVector3((Float(column) - 7) * cell, y, (Float(row) - 7) * cell)
    }

    private static func light(type: SCNLight.LightType, intensity: CGFloat, color: UIColor, position: SCNVector3) -> SCNNode {
        let node = SCNNode()
        node.light = SCNLight()
        node.light?.type = type
        node.light?.intensity = intensity
        node.light?.color = color
        node.position = position
        return node
    }

    private static func lookAt(_ target: SCNVector3) -> SCNLookAtConstraint {
        let targetNode = SCNNode()
        targetNode.position = target
        let constraint = SCNLookAtConstraint(target: targetNode)
        constraint.isGimbalLockEnabled = true
        return constraint
    }

    private static func shape(_ geometry: SCNGeometry, color: UIColor, metalness: CGFloat = 0, roughness: CGFloat, y: Float) -> SCNNode {
        geometry.firstMaterial = material(color: color, metalness: metalness, roughness: roughness)
        let node = SCNNode(geometry: geometry)
        node.position.y = y
        node.castsShadow = true
        return node
    }

    private static func material(color: UIColor, metalness: CGFloat = 0, roughness: CGFloat) -> SCNMaterial {
        let result = SCNMaterial()
        result.lightingModel = .physicallyBased
        result.diffuse.contents = color
        result.metalness.contents = metalness
        result.roughness.contents = roughness
        return result
    }

    private static func textureMaterial(named name: String, fallback: UIColor, roughness: CGFloat) -> SCNMaterial {
        let result = material(color: fallback, roughness: roughness)
        if let image = UIImage(named: name) {
            result.diffuse.contents = rgbaTexture(image)
            result.diffuse.wrapS = .repeat
            result.diffuse.wrapT = .repeat
            result.diffuse.mipFilter = .linear
            result.diffuse.contentsTransform = SCNMatrix4MakeScale(2.6, 2.6, 1)
        }
        return result
    }

    /// CoreGraphics can optimize neutral billiard textures into grayscale.
    /// Keep explicit RGBA8 backing; SceneKit's grayscale sRGB upload crashes
    /// on the simulator's Metal device (pixel format 11).
    private static func rgbaTexture(_ image: UIImage) -> UIImage {
        guard let source = image.cgImage,
              let context = CGContext(data: nil, width: source.width, height: source.height,
                                      bitsPerComponent: 8, bytesPerRow: source.width * 4,
                                      space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return image }
        context.draw(source, in: CGRect(x: 0, y: 0, width: source.width, height: source.height))
        guard let result = context.makeImage() else { return image }
        return UIImage(cgImage: result)
    }

    private static func bluePoolClothMaterial() -> SCNMaterial {
        let blue = UIColor(red: 0.026, green: 0.33, blue: 0.48, alpha: 1)
        // Dark colour scans are not roughness maps: they made the fabric look
        // lacquered and produced a sharp white reflection over the aiming line.
        let result = material(color: blue, roughness: 0.90)
        // Deterministic woven cloth, generated once with explicit RGBA backing.
        // Mipmaps soften the fibres at a distance instead of causing shimmer.
        result.diffuse.contents = poolClothTexture
        result.diffuse.wrapS = .repeat; result.diffuse.wrapT = .repeat
        result.diffuse.mipFilter = .linear
        result.diffuse.contentsTransform = SCNMatrix4MakeScale(6, 3, 1)
        return result
    }

    private static let poolClothTexture: UIImage = {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: 128, height: 128), format: format).image { context in
            for y in 0..<128 { for x in 0..<128 {
                let weave: CGFloat = (x + y).isMultiple(of: 2) ? 1.02 : 0.98
                let fibre = CGFloat((x * 73 + y * 37 + x * y * 11) % 17) / 850
                context.cgContext.setFillColor(UIColor(red: 0.026 * weave, green: 0.33 * (weave + fibre), blue: 0.48 * (weave + fibre), alpha: 1).cgColor)
                context.cgContext.fill(CGRect(x: x, y: y, width: 1, height: 1))
            } }
        }
        return rgbaTexture(image)
    }()

    /// A cloth wedge with tapered jaw ends, separate from the stable collider.
    private static func poolCushionGeometry(width: Float, height: Float, depth: Float) -> SCNGeometry {
        let x = width / 2, y = height / 2, z = depth / 2
        let points = [SCNVector3(-x,-y,-z), SCNVector3(x,-y,-z), SCNVector3(x * 0.92,-y,z), SCNVector3(-x * 0.92,-y,z),
                      SCNVector3(-x,y,-z), SCNVector3(x,y,-z), SCNVector3(x * 0.92,y * 0.18,z), SCNVector3(-x * 0.92,y * 0.18,z)]
        var vertices = [SCNVector3](), normals = [SCNVector3](), indices = [Int32]()
        for face in [[4,7,6,5], [0,1,2,3], [0,4,5,1], [3,2,6,7], [0,3,7,4], [1,5,6,2]] {
            let a = points[face[0]], b = points[face[1]], c = points[face[2]]
            let u = SCNVector3(b.x-a.x,b.y-a.y,b.z-a.z), v = SCNVector3(c.x-a.x,c.y-a.y,c.z-a.z)
            let n = SCNVector3(u.y*v.z-u.z*v.y, u.z*v.x-u.x*v.z, u.x*v.y-u.y*v.x)
            let length = sqrt(n.x*n.x+n.y*n.y+n.z*n.z)
            let normal = SCNVector3(n.x/length,n.y/length,n.z/length)
            for index in [0,1,2,0,2,3] { indices.append(Int32(vertices.count)); vertices.append(points[face[index]]); normals.append(normal) }
        }
        return SCNGeometry(sources: [SCNGeometrySource(vertices: vertices), SCNGeometrySource(normals: normals)], elements: [SCNGeometryElement(indices: indices, primitiveType: .triangles)])
    }
}
