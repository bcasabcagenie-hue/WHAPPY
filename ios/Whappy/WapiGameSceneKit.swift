import SceneKit
import UIKit

/// Native tabletop renderer shared by every WAPI game on iPhone and iPad.
/// Models are assembled from real SceneKit meshes so lighting, shadows and
/// camera movement remain physical instead of looking like a flat mock-up.
enum WapiGameSceneKit {
    static func makeScene(named game: String, dieValue: Int) -> SCNScene {
        let scene = SCNScene()
        configureStage(scene, wide: game == "Billard WAPI")
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
        camera.camera?.wantsExposureAdaptation = true
        camera.camera?.bloomIntensity = 0.14
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
        ambient.light?.intensity = 380
        scene.rootNode.addChildNode(ambient)

        let key = light(type: .spot, intensity: 1_650, color: UIColor(red: 1, green: 0.91, blue: 0.78, alpha: 1), position: SCNVector3(-4.5, 8.5, 5.5))
        key.light?.castsShadow = true
        key.light?.shadowMode = .deferred
        key.light?.shadowRadius = 7
        key.light?.shadowSampleCount = 24
        key.light?.shadowColor = UIColor.black.withAlphaComponent(0.52)
        key.light?.spotInnerAngle = 44
        key.light?.spotOuterAngle = 92
        key.constraints = [lookAt(SCNVector3(0, 0, 0))]
        scene.rootNode.addChildNode(key)

        let fill = light(type: .omni, intensity: 780, color: UIColor(red: 0.42, green: 0.68, blue: 1, alpha: 1), position: SCNVector3(5.5, 4.5, -3.8))
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
        let metalness: CGFloat = white ? 0.08 : 0.32
        root.addChildNode(shape(SCNCylinder(radius: 0.26, height: 0.08), color: body, metalness: metalness, roughness: 0.16, y: 0.05))
        root.addChildNode(shape(SCNTorus(ringRadius: 0.205, pipeRadius: 0.038), color: accent, metalness: metalness, roughness: 0.14, y: 0.105))
        root.addChildNode(shape(SCNCylinder(radius: 0.19, height: 0.06), color: body, metalness: metalness, roughness: 0.16, y: 0.14))

        switch kind {
        case "pawn":
            root.addChildNode(shape(SCNCone(topRadius: 0.105, bottomRadius: 0.17, height: 0.28), color: body, metalness: metalness, roughness: 0.16, y: 0.30))
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
            let muzzle = shape(SCNCone(topRadius: 0.035, bottomRadius: 0.105, height: 0.22), color: body, metalness: metalness, roughness: 0.14, y: 0.75)
            muzzle.eulerAngles.x = -.pi / 2.5
            muzzle.position.z = -0.16
            root.addChildNode(muzzle)
        case "bishop":
            root.addChildNode(shape(SCNCone(topRadius: 0.08, bottomRadius: 0.18, height: 0.40), color: body, metalness: metalness, roughness: 0.16, y: 0.37))
            let head = shape(SCNSphere(radius: 0.145), color: accent, metalness: metalness, roughness: 0.12, y: 0.64)
            head.scale.y = 1.35
            root.addChildNode(head)
            root.addChildNode(shape(SCNCone(topRadius: 0, bottomRadius: 0.052, height: 0.16), color: body, metalness: metalness, roughness: 0.13, y: 0.85))
        case "queen":
            root.addChildNode(shape(SCNCone(topRadius: 0.10, bottomRadius: 0.19, height: 0.48), color: body, metalness: metalness, roughness: 0.15, y: 0.41))
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
            root.addChildNode(shape(SCNCone(topRadius: 0.10, bottomRadius: 0.20, height: 0.50), color: body, metalness: metalness, roughness: 0.15, y: 0.42))
            root.addChildNode(shape(SCNSphere(radius: 0.12), color: accent, metalness: metalness, roughness: 0.1, y: 0.74))
            root.addChildNode(shape(SCNBox(width: 0.07, height: 0.23, length: 0.07, chamferRadius: 0.015), color: body, metalness: metalness, roughness: 0.12, y: 0.93))
            root.addChildNode(shape(SCNBox(width: 0.22, height: 0.065, length: 0.07, chamferRadius: 0.012), color: body, metalness: metalness, roughness: 0.12, y: 0.98))
        }
        root.childNodes.forEach { $0.castsShadow = true }
        scene.rootNode.addChildNode(root)
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

    private static func addPool(to scene: SCNScene) {
        let wood = textureMaterial(named: "WapiWalnutTexture", fallback: UIColor(red: 0.28, green: 0.09, blue: 0.025, alpha: 1), roughness: 0.24)
        let felt = textureMaterial(named: "WapiFeltTexture", fallback: UIColor(red: 0.015, green: 0.33, blue: 0.22, alpha: 1), roughness: 0.72)
        let base = SCNBox(width: 7.2, height: 0.48, length: 4.35, chamferRadius: 0.20)
        base.firstMaterial = wood
        let baseNode = SCNNode(geometry: base)
        baseNode.position.y = -0.16
        baseNode.castsShadow = true
        scene.rootNode.addChildNode(baseNode)

        let cloth = SCNBox(width: 6.45, height: 0.11, length: 3.55, chamferRadius: 0.08)
        cloth.firstMaterial = felt
        let clothNode = SCNNode(geometry: cloth)
        clothNode.position.y = 0.16
        scene.rootNode.addChildNode(clothNode)

        for z in [-2.02, 2.02] as [Float] {
            let rail = SCNBox(width: 6.75, height: 0.30, length: 0.34, chamferRadius: 0.10)
            rail.firstMaterial = wood
            let node = SCNNode(geometry: rail)
            node.position = SCNVector3(0, 0.33, z)
            node.castsShadow = true
            scene.rootNode.addChildNode(node)
            let cushion = SCNBox(width: 6.25, height: 0.19, length: 0.16, chamferRadius: 0.07)
            cushion.firstMaterial = material(color: UIColor(red: 0.012, green: 0.24, blue: 0.15, alpha: 1), roughness: 0.60)
            let cushionNode = SCNNode(geometry: cushion)
            cushionNode.position = SCNVector3(0, 0.31, z > 0 ? z - 0.23 : z + 0.23)
            scene.rootNode.addChildNode(cushionNode)
        }
        for x in [-3.45, 3.45] as [Float] {
            let rail = SCNBox(width: 0.34, height: 0.30, length: 3.75, chamferRadius: 0.10)
            rail.firstMaterial = wood
            let node = SCNNode(geometry: rail)
            node.position = SCNVector3(x, 0.33, 0)
            node.castsShadow = true
            scene.rootNode.addChildNode(node)
            let cushion = SCNBox(width: 0.16, height: 0.19, length: 3.35, chamferRadius: 0.07)
            cushion.firstMaterial = material(color: UIColor(red: 0.012, green: 0.24, blue: 0.15, alpha: 1), roughness: 0.60)
            let cushionNode = SCNNode(geometry: cushion)
            cushionNode.position = SCNVector3(x > 0 ? x - 0.23 : x + 0.23, 0.31, 0)
            scene.rootNode.addChildNode(cushionNode)
        }

        let pocketLocations: [(Float, Float)] = [(-3.18, -1.75), (0, -1.83), (3.18, -1.75), (-3.18, 1.75), (0, 1.83), (3.18, 1.75)]
        for (x, z) in pocketLocations {
            let pocket = SCNCylinder(radius: 0.245, height: 0.075)
            pocket.firstMaterial = material(color: .black, roughness: 0.92)
            let pocketNode = SCNNode(geometry: pocket)
            pocketNode.position = SCNVector3(x, 0.25, z)
            scene.rootNode.addChildNode(pocketNode)
        }

        let ballColors: [UIColor] = [.systemYellow, .systemBlue, .systemRed, .systemPurple, .systemOrange, .systemGreen, UIColor(red: 0.52, green: 0.02, blue: 0.04, alpha: 1), .black]
        var ballID = 1
        for row in 0..<5 {
            for index in 0...row {
                let id = ballID
                let x = Float(row) * 0.255 + 0.65
                let z = (Float(index) - Float(row) / 2) * 0.295
                addPoolBall(id: id, color: ballColors[(id - 1) % ballColors.count], striped: id > 8, at: SCNVector3(x, 0.40, z), to: scene)
                ballID += 1
            }
        }
        addPoolBall(id: 0, color: .white, striped: false, at: SCNVector3(-1.65, 0.40, 0), to: scene)

        let shaft = SCNCylinder(radius: 0.035, height: 3.7)
        shaft.firstMaterial = material(color: UIColor(red: 0.84, green: 0.56, blue: 0.24, alpha: 1), roughness: 0.20)
        let cue = SCNNode(geometry: shaft)
        cue.name = "wapi.game.cue"
        cue.position = SCNVector3(-3.0, 0.52, 0)
        cue.eulerAngles = SCNVector3(0, 0, -Float.pi / 2)
        cue.castsShadow = true
        cue.runAction(.repeatForever(.sequence([
            .moveBy(x: 0.12, y: 0, z: 0, duration: 0.70),
            .moveBy(x: -0.12, y: 0, z: 0, duration: 0.70),
        ])))
        scene.rootNode.addChildNode(cue)
    }

    private static func addPoolBall(id: Int, color: UIColor, striped: Bool, at position: SCNVector3, to scene: SCNScene) {
        let root = SCNNode()
        root.position = position
        let ball = SCNSphere(radius: 0.145)
        ball.segmentCount = 48
        ball.firstMaterial = material(color: striped ? .white : color, metalness: 0.03, roughness: 0.08)
        root.addChildNode(SCNNode(geometry: ball))
        if striped {
            let stripe = SCNTorus(ringRadius: 0.112, pipeRadius: 0.051)
            stripe.ringSegmentCount = 48
            stripe.pipeSegmentCount = 16
            root.addChildNode(shape(stripe, color: color, roughness: 0.08, y: 0))
        }
        if id != 0 {
            let badge = SCNCylinder(radius: 0.050, height: 0.006)
            badge.firstMaterial = material(color: .white, roughness: 0.14)
            let badgeNode = SCNNode(geometry: badge)
            badgeNode.position.y = 0.143
            root.addChildNode(badgeNode)
        }
        root.castsShadow = true
        scene.rootNode.addChildNode(root)
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
            result.diffuse.contents = image
            result.diffuse.wrapS = .repeat
            result.diffuse.wrapT = .repeat
            result.diffuse.mipFilter = .linear
            result.diffuse.contentsTransform = SCNMatrix4MakeScale(2.6, 2.6, 1)
        }
        return result
    }
}
