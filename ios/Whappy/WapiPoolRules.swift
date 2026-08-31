import Foundation

/// Eight-ball house rules shared with WAPI's server: a scratch, wrong first
/// contact or no contact is a foul. These local results never award online points.
enum WapiPoolRules {
    struct Result {
        let shooterGroup: Int
        let opponentGroup: Int
        let foul: Bool
        let keepTurn: Bool
        let shooterWon: Bool?
    }
    static func group(of id: Int) -> Int { (1...7).contains(id) ? 1 : (9...15).contains(id) ? 2 : 0 }
    static func targets(group: Int, remaining: Set<Int>) -> Set<Int> {
        if group == 0 { return remaining.subtracting([0, 8]) }
        let own = remaining.filter { Self.group(of: $0) == group }
        return own.isEmpty ? [8] : Set(own)
    }
    static func resolve(before: Set<Int>, shooterGroup: Int, opponentGroup: Int, first: Int?, scratched: Bool, pocketed: Set<Int>) -> Result {
        let legalTargets = targets(group: shooterGroup, remaining: before)
        let foul = scratched || first == nil || !legalTargets.contains(first ?? -1)
        if pocketed.contains(8) {
            let legalEight = shooterGroup != 0 && legalTargets == [8] && !foul
            return Result(shooterGroup: shooterGroup, opponentGroup: opponentGroup, foul: foul, keepTurn: false, shooterWon: legalEight)
        }
        var own = shooterGroup
        var other = opponentGroup
        if !foul && own == 0, let firstMade = pocketed.sorted().first(where: { group(of: $0) != 0 }) {
            own = group(of: firstMade); other = own == 1 ? 2 : 1
        }
        return Result(shooterGroup: own, opponentGroup: other, foul: foul,
                      keepTurn: !foul && pocketed.contains(where: { group(of: $0) == own && own != 0 }), shooterWon: nil)
    }
}

struct WapiPoolShotOutcome {
    let remaining: Set<Int>
    let pocketed: Set<Int>
    let firstContact: Int?
    let scratched: Bool
    let aiShots: [Int: (Float, Int)]
}
