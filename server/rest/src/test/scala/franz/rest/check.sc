val ParamR = ".*?(\\w+) *: *([A-Za-z0-9\\[\\]]+).*".r
val ParamR(a,b) = "  partition: Option[Int] = None"
println(a)